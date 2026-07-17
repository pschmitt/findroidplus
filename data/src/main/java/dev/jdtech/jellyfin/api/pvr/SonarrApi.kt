package dev.jdtech.jellyfin.api.pvr

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin client for a single Sonarr instance. Cheap to construct per-call - [baseUrl] and [apiKey]
 * are resolved by the caller (typically from [dev.jdtech.jellyfin.security.SecureCredentialStore]
 * and the Sonarr settings) rather than injected as a Hilt singleton, since the user can
 * reconfigure either at runtime. Only a single Sonarr instance is supported, matching the app's
 * existing single-Jellyfin-server assumption.
 */
class SonarrApi(private val baseUrl: String, private val apiKey: String) {
    private val client by lazy { PvrHttpClient.create(apiKey, PvrService.SONARR) }

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

    suspend fun getCalendar(start: LocalDate, end: LocalDate): List<SonarrCalendarEntry> =
        withContext(Dispatchers.IO) {
            // includeSeries=true embeds the series object (with tvdbId) on each entry, so no
            // separate getSeries() call/join is needed to resolve tvdbId (unlike getQueue()).
            // LocalDate.toString() already produces the YYYY-MM-DD format this endpoint expects.
            val url =
                buildUrl(
                    "api",
                    "v3",
                    "calendar",
                    queryParams =
                        mapOf(
                            "start" to start.toString(),
                            "end" to end.toString(),
                            "includeSeries" to "true",
                        ),
                )
            json.decodeFromString<List<SonarrCalendarEntry>>(execute(url))
        }

    suspend fun getEpisodes(seriesId: Int): List<SonarrEpisodeDto> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "episode", queryParams = mapOf("seriesId" to seriesId.toString()))
            json.decodeFromString<List<SonarrEpisodeDto>>(execute(url))
        }

    /** The configured TV-shows storage location(s), with free/total space already embedded. */
    suspend fun getRootFolders(): List<PvrRootFolderDto> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "rootfolder")
            json.decodeFromString<List<PvrRootFolderDto>>(execute(url))
        }

    /**
     * Triggers an automatic search - Sonarr picks and grabs the best release itself. Returns the
     * queued command's id (see [getCommandStatus]), not the result - Sonarr answers this as soon
     * as the command is queued, well before the search itself finishes.
     */
    suspend fun searchEpisode(episodeId: Int): Int =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "command")
            val body = json.encodeToString(SonarrCommandRequest(name = "EpisodeSearch", episodeIds = listOf(episodeId)))
            json.decodeFromString<PvrCommandResponse>(execute(url, body)).id
        }

    /** Triggers Sonarr's automatic search for every missing episode in a series. */
    suspend fun searchSeries(seriesId: Int): Int =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "command")
            val body = json.encodeToString(SonarrSeriesCommandRequest(name = "SeriesSearch", seriesId = seriesId))
            json.decodeFromString<PvrCommandResponse>(execute(url, body)).id
        }

    /** Current status ("queued"/"started"/"completed"/"failed"/...) of a command started via [searchEpisode]. */
    suspend fun getCommandStatus(commandId: Int): PvrCommandResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "command", commandId.toString())
            json.decodeFromString<PvrCommandResponse>(execute(url))
        }

    /** Single-episode lookup - see [SonarrEpisodeDetail]. */
    suspend fun getEpisodeById(episodeId: Int): SonarrEpisodeDetail =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "episode", episodeId.toString())
            json.decodeFromString<SonarrEpisodeDetail>(execute(url))
        }

    /**
     * Lists candidate releases for an episode (interactive/manual search), without grabbing any.
     * Sonarr answers this synchronously only once it has polled every enabled indexer (directly or
     * via Prowlarr), which can comfortably exceed the default read timeout when an indexer is slow
     * - so this call gets a longer, dedicated timeout rather than the shared default. [readTimeoutMs]
     * comes from `AppPreferences.pvrSearchTimeout` (Settings > Network), since how long is
     * reasonable to wait depends entirely on the user's indexers.
     */
    suspend fun getReleases(episodeId: Int, readTimeoutMs: Long): List<PvrRelease> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "release", queryParams = mapOf("episodeId" to episodeId.toString()))
            json.decodeFromString<List<PvrRelease>>(execute(url, readTimeoutMs = readTimeoutMs))
        }

    /** Grabs a specific release returned by [getReleases]. */
    suspend fun grabRelease(guid: String, indexerId: Int): Unit =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "release")
            val body = json.encodeToString(PvrGrabReleaseRequest(guid = guid, indexerId = indexerId))
            execute(url, body)
        }

    /**
     * Removes a queue item. [removeFromClient] also deletes the download (and its data) in the
     * download client; [blocklist] prevents Sonarr from grabbing the same release again. There is
     * deliberately no "pause": the v3 API exposes none - pausing lives in the download client.
     */
    suspend fun deleteQueueItem(queueItemId: Int, removeFromClient: Boolean, blocklist: Boolean): Unit =
        withContext(Dispatchers.IO) {
            val url =
                buildUrl(
                    "api",
                    "v3",
                    "queue",
                    queueItemId.toString(),
                    queryParams =
                        mapOf(
                            "removeFromClient" to removeFromClient.toString(),
                            "blocklist" to blocklist.toString(),
                        ),
                )
            execute(url, delete = true)
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

    /**
     * [jsonBody] `null` issues a GET; otherwise a POST with that body as the JSON payload.
     * [delete] issues a DELETE instead. [readTimeoutMs] overrides [PvrHttpClient]'s default read
     * timeout for this call only.
     */
    private fun execute(
        url: String,
        jsonBody: String? = null,
        readTimeoutMs: Long? = null,
        delete: Boolean = false,
    ): String {
        val request =
            Request.Builder()
                .url(url)
                .apply {
                    when {
                        delete -> delete()
                        jsonBody != null ->
                            post(jsonBody.toRequestBody("application/json".toMediaType()))
                        else -> get()
                    }
                }
                .build()
        val callClient =
            if (readTimeoutMs != null) {
                client.newBuilder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()
            } else {
                client
            }
        callClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val snippet = body.take(200).ifBlank { "(empty body)" }
                throw PvrApiException(
                    "Sonarr request to $url failed with HTTP ${response.code}: $snippet",
                    httpCode = response.code,
                )
            }
            return body
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
