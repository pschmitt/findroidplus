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
    suspend fun getDetails(
        tmdbId: Int,
        mediaType: SeerrMediaType,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): Result<SeerrMediaDetail>

    /**
     * Requests the item. When [seasonNumber] is `null`, a series request covers all seasons;
     * when set, only that season is requested. Ignored for movies.
     */
    suspend fun request(
        tmdbId: Int,
        mediaType: SeerrMediaType,
        seasonNumber: Int? = null,
    ): Result<Unit>

    /** Most recent requests first, titles/posters already resolved. */
    suspend fun getRecentRequests(limit: Int = 20): Result<List<SeerrRequestItem>>

    /** Cancels/deletes a request - Seerr also un-monitors it on the Sonarr/Radarr side. */
    suspend fun cancelRequest(requestId: Int): Result<Unit>

    /**
     * TMDB season poster URLs for [seasonNumbers] of the series [tmdbId] - fetched in parallel,
     * one `GET /tv/{tmdbId}/season/{n}` call per season (TMDB's show-level detail response
     * doesn't carry per-season poster paths, only [getDetails]'s season-scoped call does). Used
     * for the Show screen's missing-season placeholder cards, so they show real artwork instead
     * of a generic icon. A season whose lookup fails maps to `null` rather than failing the whole
     * batch - one bad season shouldn't blank out the others.
     */
    suspend fun getSeasonPosterUrls(tmdbId: Int, seasonNumbers: List<Int>): Result<Map<Int, String?>>
}
