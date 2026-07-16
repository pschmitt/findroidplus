package dev.jdtech.jellyfin.api.pvr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Thin client for a single Sonarr instance. Cheap to construct per-call - [baseUrl] and [apiKey]
 * are resolved by the caller (typically from [dev.jdtech.jellyfin.security.SecureCredentialStore]
 * and the Sonarr settings) rather than injected as a Hilt singleton, since the user can
 * reconfigure either at runtime. Only a single Sonarr instance is supported, matching the app's
 * existing single-Jellyfin-server assumption.
 */
class SonarrApi(private val baseUrl: String, private val apiKey: String) {
    private val client by lazy { PvrHttpClient.create(apiKey) }

    suspend fun getSeries(): List<SonarrSeries> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "series")
            json.decodeFromString<List<SonarrSeries>>(execute(url))
        }

    suspend fun getQueue(): List<SonarrQueueItem> =
        withContext(Dispatchers.IO) {
            // Sonarr paginates the queue endpoint (default page size 10-20); requesting a large
            // pageSize is the standard workaround to get everything in a single call rather than
            // looping through pages. includeEpisode=true embeds the episode number, which Findroid
            // needs to resolve the matching Jellyfin episode (see SonarrQueueItem/SonarrEpisode).
            val url =
                buildUrl(
                    "api",
                    "v3",
                    "queue",
                    queryParams = mapOf("pageSize" to "250", "includeEpisode" to "true"),
                )
            val response = json.decodeFromString<SonarrQueueResponse>(execute(url))
            response.records
        }

    private fun buildUrl(
        vararg pathSegments: String,
        queryParams: Map<String, String> = emptyMap(),
    ): String {
        val builder = baseUrl.toHttpUrl().newBuilder()
        pathSegments.forEach { builder.addPathSegments(it) }
        queryParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun execute(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PvrApiException("Sonarr request to $url failed with HTTP ${response.code}")
            }
            return response.body.string()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
