package dev.jdtech.jellyfin.api.pvr

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Thin client for a single Radarr instance. Cheap to construct per-call - [baseUrl] and [apiKey]
 * are resolved by the caller (typically from [dev.jdtech.jellyfin.security.SecureCredentialStore]
 * and the Radarr settings) rather than injected as a Hilt singleton, since the user can
 * reconfigure either at runtime. Only a single Radarr instance is supported, matching the app's
 * existing single-Jellyfin-server assumption.
 */
class RadarrApi(private val baseUrl: String, private val apiKey: String) {
    private val client by lazy { PvrHttpClient.create(apiKey) }

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
