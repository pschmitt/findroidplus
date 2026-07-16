package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.models.AutomaticSearchOutcome

/**
 * Triggers Radarr movie searches - the movie counterpart of [SonarrSearchRepository]. Unlike the
 * read-only Radarr repositories ([CalendarRepository], `QueueStatusRepository`), these are
 * user-initiated actions, so failures are surfaced via [Result] rather than swallowed into an
 * empty/default value.
 */
interface RadarrSearchRepository {
    /**
     * Resolves Radarr's internal numeric movie id for a movie, matched by its TMDB id (the only
     * provider id both Jellyfin's movie metadata and Radarr are guaranteed to share). `null` when
     * Radarr isn't configured or doesn't know the movie.
     */
    suspend fun resolveMovieId(tmdbId: String): Int?

    /**
     * Triggers an automatic search - Radarr picks and grabs the best release itself. Returns once
     * the search is *queued*, not once it finishes (see [awaitAutomaticSearchResult] for that) -
     * on success, also schedules a background check so the user gets notified once it actually
     * completes, even if they've since left the screen.
     */
    suspend fun searchMovie(movieId: Int): Result<Unit>

    /**
     * Polls Radarr until the automatic search command started by [searchMovie] reaches a terminal
     * state, then looks up the movie's title for a human-readable notification. Meant to be called
     * from a background worker, not the triggering screen's ViewModel - see
     * `dev.jdtech.jellyfin.work.AutomaticSearchWorker`.
     */
    suspend fun awaitAutomaticSearchResult(movieId: Int, commandId: Int): Result<AutomaticSearchOutcome>

    /**
     * Lists candidate releases for a movie (interactive/manual search). Results are cached per
     * movie for `AppPreferences.pvrReleaseCacheMinutes` (Settings > Integrations, see
     * [RadarrSearchRepositoryImpl]) since this call is expensive - Radarr polls every enabled
     * indexer (directly or via a proxy like Prowlarr) before answering.
     */
    suspend fun getReleases(movieId: Int): Result<List<PvrRelease>>

    /** Grabs a specific release returned by [getReleases]. */
    suspend fun grabRelease(release: PvrRelease): Result<Unit>
}
