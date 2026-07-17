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
 * Thin client for a single Radarr instance. Cheap to construct per-call - [baseUrl] and [apiKey]
 * are resolved by the caller (typically from [dev.jdtech.jellyfin.security.SecureCredentialStore]
 * and the Radarr settings) rather than injected as a Hilt singleton, since the user can
 * reconfigure either at runtime. Only a single Radarr instance is supported, matching the app's
 * existing single-Jellyfin-server assumption.
 */
class RadarrApi(private val baseUrl: String, private val apiKey: String) {
    private val client by lazy { PvrHttpClient.create(apiKey, PvrService.RADARR) }

    suspend fun getMovie(): List<RadarrMovie> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "movie")
            json.decodeFromString<List<RadarrMovie>>(execute(url))
        }

    suspend fun getQueue(): List<RadarrQueueItem> =
        withContext(Dispatchers.IO) {
            // Radarr paginates the queue endpoint (default page size 10-20); requesting a large
            // pageSize is the standard workaround to get everything in a single call rather than
            // looping through pages.
            val url = buildUrl("api", "v3", "queue", queryParams = mapOf("pageSize" to "250"))
            val response = json.decodeFromString<RadarrQueueResponse>(execute(url))
            response.records
        }

    suspend fun getCalendar(start: LocalDate, end: LocalDate): List<RadarrCalendarEntry> =
        withContext(Dispatchers.IO) {
            // Radarr's calendar entries are full movie objects (tmdbId already present per
            // entry), so no separate getMovie() call/join is needed, unlike getQueue(). Radarr has
            // no includeSeries-style flag to pass here. LocalDate.toString() already produces the
            // YYYY-MM-DD format this endpoint expects.
            val url =
                buildUrl(
                    "api",
                    "v3",
                    "calendar",
                    queryParams = mapOf("start" to start.toString(), "end" to end.toString()),
                )
            json.decodeFromString<List<RadarrCalendarEntry>>(execute(url))
        }

    /**
     * Triggers an automatic search - Radarr picks and grabs the best release itself. Returns the
     * queued command's id (see [getCommandStatus]), not the result - Radarr answers this as soon
     * as the command is queued, well before the search itself finishes.
     */
    suspend fun searchMovie(movieId: Int): Int =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "command")
            val body = json.encodeToString(RadarrCommandRequest(name = "MoviesSearch", movieIds = listOf(movieId)))
            json.decodeFromString<PvrCommandResponse>(execute(url, body)).id
        }

    /** Current status ("queued"/"started"/"completed"/"failed"/...) of a command started via [searchMovie]. */
    suspend fun getCommandStatus(commandId: Int): PvrCommandResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "command", commandId.toString())
            json.decodeFromString<PvrCommandResponse>(execute(url))
        }

    /** Single-movie lookup, e.g. for a human-readable notification title. */
    suspend fun getMovieById(movieId: Int): RadarrMovie =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "movie", movieId.toString())
            json.decodeFromString<RadarrMovie>(execute(url))
        }

    /**
     * Lists candidate releases for a movie (interactive/manual search), without grabbing any.
     * Radarr answers this synchronously only once it has polled every enabled indexer (directly or
     * via Prowlarr), which can comfortably exceed the default read timeout when an indexer is slow
     * - so this call gets a longer, dedicated timeout rather than the shared default. [readTimeoutMs]
     * comes from `AppPreferences.pvrSearchTimeout` (Settings > Network), since how long is
     * reasonable to wait depends entirely on the user's indexers.
     */
    suspend fun getReleases(movieId: Int, readTimeoutMs: Long): List<PvrRelease> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v3", "release", queryParams = mapOf("movieId" to movieId.toString()))
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
     * download client; [blocklist] prevents Radarr from grabbing the same release again. There is
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
                    "Radarr request to $url failed with HTTP ${response.code}: $snippet",
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
