package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.SeerrMediaDetail
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem

/**
 * Discover/request flows backed by a Seerr/Seerr instance - the "add new content" side of
 * the PVR integration. Seerr owns the Sonarr/Radarr routing (quality profiles, root
 * folders), so Findroid only ever searches and files requests. All calls are user-initiated, so
 * failures are surfaced via [Result] rather than swallowed, same as the search repositories.
 */
interface SeerrRepository {
    /** TMDB-backed movie/series search, with each result's current availability in Seerr. */
    suspend fun search(query: String): Result<List<SeerrSearchItem>>

    /** Trending movies and series - the Home screen's mixed discovery row. */
    suspend fun getTrending(): Result<List<SeerrSearchItem>>

    /** Popular movies, popularity-sorted. */
    suspend fun getPopularMovies(): Result<List<SeerrSearchItem>>

    /** Popular series, popularity-sorted. */
    suspend fun getPopularShows(): Result<List<SeerrSearchItem>>

    /** Full detail payload for the dedicated media view, including open request ids. */
    suspend fun getDetails(tmdbId: Int, mediaType: SeerrMediaType): Result<SeerrMediaDetail>

    /** Requests the item; series requests cover all seasons. */
    suspend fun request(tmdbId: Int, mediaType: SeerrMediaType): Result<Unit>

    /** Most recent requests first, titles/posters already resolved. */
    suspend fun getRecentRequests(limit: Int = 20): Result<List<SeerrRequestItem>>

    /** Cancels/deletes a request - Seerr also un-monitors it on the Sonarr/Radarr side. */
    suspend fun cancelRequest(requestId: Int): Result<Unit>
}
