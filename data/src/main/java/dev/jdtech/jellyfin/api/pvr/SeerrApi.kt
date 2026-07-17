package dev.jdtech.jellyfin.api.pvr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin client for a single Seerr (formerly Jellyseerr) instance, authenticated via `X-Api-Key` like the
 * Sonarr/Radarr clients (shared [PvrHttpClient]). Cheap to construct per-call - [baseUrl] and
 * [apiKey] are resolved by the caller (typically from
 * [dev.jdtech.jellyfin.security.SecureCredentialStore] and the Seerr settings) rather than
 * injected as a Hilt singleton, since the user can reconfigure either at runtime.
 */
class SeerrApi(private val baseUrl: String, private val apiKey: String) {
    private val client by lazy { PvrHttpClient.create(apiKey, PvrService.SEERR) }

    /** Combined TMDB-backed movie/series/person search. */
    suspend fun search(query: String, page: Int = 1): SeerrSearchResponse =
        withContext(Dispatchers.IO) {
            val url =
                buildUrl(
                    "api",
                    "v1",
                    "search",
                    queryParams = mapOf("query" to query, "page" to page.toString()),
                )
            json.decodeFromString<SeerrSearchResponse>(execute(url))
        }

    /**
     * Discovery lists for the Home screen - same result shape as [search]. [path] is the
     * discover endpoint's last segment: "trending" (mixed movies/series), "movies" or "tv"
     * (both popularity-sorted).
     */
    suspend fun discover(path: String, page: Int = 1): SeerrSearchResponse =
        withContext(Dispatchers.IO) {
            val url =
                buildUrl(
                    "api",
                    "v1",
                    "discover",
                    path,
                    queryParams = mapOf("page" to page.toString()),
                )
            json.decodeFromString<SeerrSearchResponse>(execute(url))
        }

    /**
     * Files a request; Seerr routes it to Sonarr/Radarr with the server-side defaults
     * (quality profile, root folder). Series requests ask for all seasons - Seerr's
     * "seasons": "all" shorthand - since Findroid doesn't offer per-season picking (yet).
     */
    suspend fun createRequest(mediaType: String, tmdbId: Int) {
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v1", "request")
            val body =
                SeerrCreateRequestBody(
                    mediaType = mediaType,
                    mediaId = tmdbId,
                    seasons = if (mediaType == MEDIA_TYPE_TV) JsonPrimitive("all") else null,
                )
            execute(url, json.encodeToString(body))
        }
    }

    /** Most recent requests first. */
    suspend fun getRequests(take: Int): SeerrRequestsResponse =
        withContext(Dispatchers.IO) {
            val url =
                buildUrl(
                    "api",
                    "v1",
                    "request",
                    queryParams = mapOf("take" to take.toString(), "sort" to "added"),
                )
            json.decodeFromString<SeerrRequestsResponse>(execute(url))
        }

    suspend fun getMovieDetails(tmdbId: Int): SeerrMovieDetails =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v1", "movie", tmdbId.toString())
            json.decodeFromString<SeerrMovieDetails>(execute(url))
        }

    suspend fun getTvDetails(tmdbId: Int): SeerrTvDetails =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v1", "tv", tmdbId.toString())
            json.decodeFromString<SeerrTvDetails>(execute(url))
        }

    /** Cancels/deletes a request filed via [createRequest] (or any request the API key may manage). */
    suspend fun deleteRequest(requestId: Int) {
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v1", "request", requestId.toString())
            execute(url, delete = true)
        }
    }

    /** Validates base URL + API key in one call - see [SeerrUser]. */
    suspend fun getCurrentUser(): SeerrUser =
        withContext(Dispatchers.IO) {
            val url = buildUrl("api", "v1", "auth", "me")
            json.decodeFromString<SeerrUser>(execute(url))
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
     * [delete] issues a DELETE instead.
     */
    private fun execute(url: String, jsonBody: String? = null, delete: Boolean = false): String {
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
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val snippet = body.take(200).ifBlank { "(empty body)" }
                throw PvrApiException(
                    "Seerr request to $url failed with HTTP ${response.code}: $snippet",
                    httpCode = response.code,
                )
            }
            return body
        }
    }

    companion object {
        const val MEDIA_TYPE_MOVIE = "movie"
        const val MEDIA_TYPE_TV = "tv"

        const val DISCOVER_TRENDING = "trending"
        const val DISCOVER_MOVIES = "movies"
        const val DISCOVER_TV = "tv"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
