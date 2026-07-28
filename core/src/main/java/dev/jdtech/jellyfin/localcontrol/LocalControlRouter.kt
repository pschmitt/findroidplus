package dev.jdtech.jellyfin.localcontrol

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.api.pvr.PvrHttpClient
import dev.jdtech.jellyfin.api.pvr.PvrService
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Dispatches an already-authenticated request (see [LocalControlServer], which verifies the
 * bearer token before ever calling this) to the actual app state it controls - real
 * [AppPreferences] download settings, the real current download list, a real
 * [Downloader]-triggered download, or a raw debug request against Jellyfin/Sonarr/Radarr/Seerr
 * using the app's own stored credentials.
 */
@Singleton
class LocalControlRouter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val secureCredentialStore: SecureCredentialStore,
    private val jellyfinRepository: JellyfinRepository,
    private val downloader: Downloader,
    private val pvrConfiguration: PvrConfiguration,
) {
    suspend fun handle(method: String, path: String, body: JsonElement?): LocalControlResponse =
        withContext(Dispatchers.IO) {
            try {
                when {
                    method == "GET" && path == "/settings/downloads" -> getDownloadSettings()
                    method == "PATCH" && path == "/settings/downloads" ->
                        patchDownloadSettings(body)
                    method == "GET" && path == "/downloads" -> listDownloads()
                    method == "POST" && path == "/downloads/trigger" -> triggerDownload(body)
                    method == "POST" && path == "/debug/proxy" -> debugProxy(body)
                    else ->
                        LocalControlResponse(
                            LocalControlStatus.NOT_FOUND,
                            errorBody("Unknown endpoint: $method $path"),
                        )
                }
            } catch (e: Exception) {
                Timber.e(e, "Local control request failed: %s %s", method, path)
                LocalControlResponse(LocalControlStatus.INTERNAL_ERROR, errorBody(e.message ?: "Internal error"))
            }
        }

    private fun getDownloadSettings(): LocalControlResponse =
        LocalControlResponse(LocalControlStatus.OK, DownloadSettingsBridge.toJson(appPreferences))

    private fun patchDownloadSettings(body: JsonElement?): LocalControlResponse {
        val patch = body?.jsonObject
        if (patch == null) {
            return LocalControlResponse(LocalControlStatus.BAD_REQUEST, errorBody("Expected a JSON object body"))
        }
        DownloadSettingsBridge.applyPatch(appPreferences, patch)
        return LocalControlResponse(LocalControlStatus.OK, DownloadSettingsBridge.toJson(appPreferences))
    }

    private suspend fun listDownloads(): LocalControlResponse {
        val items = jellyfinRepository.getDownloads()
        val body = buildJsonArray {
            items.forEach { item ->
                add(
                    buildJsonObject {
                        put("itemId", item.id.toString())
                        put("name", item.name)
                        put(
                            "sources",
                            buildJsonArray {
                                item.sources
                                    .filter { it.type == FindroidSourceType.LOCAL }
                                    .forEach { source ->
                                        add(
                                            buildJsonObject {
                                                put("sourceId", source.id)
                                                put("name", source.name)
                                                put("sizeBytes", source.size)
                                                source.downloadId?.let { put("downloadId", it) }
                                            }
                                        )
                                    }
                            },
                        )
                    }
                )
            }
        }
        return LocalControlResponse(LocalControlStatus.OK, body)
    }

    private suspend fun triggerDownload(body: JsonElement?): LocalControlResponse {
        val obj = body?.jsonObject
        val itemId = obj?.get("itemId")?.jsonPrimitive?.contentOrNull
        if (itemId == null) {
            return LocalControlResponse(LocalControlStatus.BAD_REQUEST, errorBody("Missing \"itemId\""))
        }
        val uuid =
            runCatching { UUID.fromString(itemId) }.getOrNull()
                ?: return LocalControlResponse(LocalControlStatus.BAD_REQUEST, errorBody("Invalid itemId"))
        val item =
            jellyfinRepository.getItem(uuid)
                ?: return LocalControlResponse(LocalControlStatus.NOT_FOUND, errorBody("Item not found"))
        val sourceId =
            obj["sourceId"]?.jsonPrimitive?.contentOrNull ?: item.sources.firstOrNull()?.id
        if (sourceId == null) {
            return LocalControlResponse(LocalControlStatus.CONFLICT, errorBody("Item has no media sources"))
        }
        val (downloadId, error) =
            downloader.downloadItem(item, sourceId, downloader.resolvePreferredStorageIndex())
        return if (error == null) {
            LocalControlResponse(LocalControlStatus.OK, buildJsonObject { put("downloadId", downloadId) })
        } else {
            LocalControlResponse(LocalControlStatus.CONFLICT, errorBody(error.asString(context.resources)))
        }
    }

    private fun debugProxy(body: JsonElement?): LocalControlResponse {
        val obj = body?.jsonObject
        val service = obj?.get("service")?.jsonPrimitive?.contentOrNull
        val method = obj?.get("method")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
        val path = obj?.get("path")?.jsonPrimitive?.contentOrNull
        if (service == null || path == null) {
            return LocalControlResponse(
                LocalControlStatus.BAD_REQUEST,
                errorBody("Expected \"service\" and \"path\""),
            )
        }
        val query = obj["query"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        val requestBody = obj["body"]?.let { if (it is JsonNull) null else it.toString() }

        val (baseUrl, client) = resolveProxyClient(service) ?: return LocalControlResponse(
            LocalControlStatus.CONFLICT,
            errorBody("\"$service\" is not configured/enabled on this device"),
        )

        val url =
            baseUrl.toHttpUrl().newBuilder().apply {
                path.trim('/').split('/').forEach { segment -> if (segment.isNotEmpty()) addPathSegment(segment) }
                query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()

        val request =
            Request.Builder().url(url).apply {
                when (method) {
                    "DELETE" -> delete()
                    "PUT" -> put(requestBody.orEmpty().toRequestBody("application/json".toMediaType()))
                    "POST" -> post(requestBody.orEmpty().toRequestBody("application/json".toMediaType()))
                    else -> get()
                }
            }.build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            return LocalControlResponse(
                response.code,
                buildJsonObject {
                    put("body", JsonPrimitive(responseBody))
                },
            )
        }
    }

    /** Base URL + an authenticated [OkHttpClient] for [service], or `null` if it isn't
     * configured/enabled - mirrors the same "is this usable" gate [PvrConfiguration] already
     * enforces for the app's own PVR UI, so the debug proxy can't be used to probe a service the
     * user hasn't actually set up. */
    private fun resolveProxyClient(service: String): Pair<String, OkHttpClient>? =
        when (service) {
            "jellyfin" -> {
                val baseUrl = jellyfinRepository.getBaseUrl().ifBlank { null } ?: return null
                val token = jellyfinRepository.getAccessToken() ?: return null
                baseUrl to
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain.request().newBuilder().header("X-Emby-Token", token).build()
                            )
                        }
                        .build()
            }
            "sonarr" -> {
                if (!pvrConfiguration.isSonarrConfigured()) return null
                val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl) ?: return null
                val apiKey = secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) ?: return null
                baseUrl to PvrHttpClient.create(apiKey, PvrService.SONARR)
            }
            "radarr" -> {
                if (!pvrConfiguration.isRadarrConfigured()) return null
                val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl) ?: return null
                val apiKey = secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) ?: return null
                baseUrl to PvrHttpClient.create(apiKey, PvrService.RADARR)
            }
            "seerr" -> {
                if (!pvrConfiguration.isSeerrConfigured()) return null
                val baseUrl = appPreferences.getValue(appPreferences.seerrBaseUrl) ?: return null
                val apiKey = secureCredentialStore.getString(PvrCredentialKeys.SEERR_API_KEY) ?: return null
                baseUrl to PvrHttpClient.create(apiKey, PvrService.SEERR)
            }
            else -> null
        }

    private fun errorBody(message: String): JsonObject = buildJsonObject { put("error", message) }
}
