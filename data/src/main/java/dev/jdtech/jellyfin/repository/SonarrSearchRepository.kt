package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.models.AutomaticSearchOutcome

/**
 * Triggers Sonarr episode searches - unlike the read-only Sonarr repositories
 * ([SeasonEpisodesRepository], [CalendarRepository], `QueueStatusRepository`), these are
 * user-initiated actions, so failures are surfaced via [Result] rather than swallowed into an
 * empty/default value.
 */
interface SonarrSearchRepository {
    /**
     * Triggers Sonarr's automatic search for all missing episodes in the series identified by its
     * TMDB id. A series-level search has no single episode completion to observe, unlike
     * [searchEpisode].
     */
    suspend fun searchSeriesByTmdbId(tmdbId: Int): Result<Unit>

    /**
     * Resolves Sonarr's internal numeric episode id for a show/season/episode, needed by callers
     * that only have a [dev.jdtech.jellyfin.models.FindroidEpisode] (whose tvdb id and
     * season/episode numbers are the only thing Findroid can match against Sonarr) rather than a
     * cached Sonarr id (see [dev.jdtech.jellyfin.models.UpcomingEpisode.episodeId]/
     * [dev.jdtech.jellyfin.models.CalendarEntry.episodeId]). `null` when Sonarr isn't configured
     * or doesn't know the episode.
     */
    suspend fun resolveEpisodeId(seriesTvdbId: String, seasonNumber: Int, episodeNumber: Int): Int?

    /**
     * Triggers an automatic search - Sonarr picks and grabs the best release itself. Returns once
     * the search is *queued*, not once it finishes (see [awaitAutomaticSearchResult] for that) -
     * on success, also schedules a background check so the user gets notified once it actually
     * completes, even if they've since left the screen.
     */
    suspend fun searchEpisode(episodeId: Int): Result<Unit>

    /**
     * Polls Sonarr until the automatic search command started by [searchEpisode] reaches a
     * terminal state, then looks up the episode's title for a human-readable notification. Meant
     * to be called from a background worker, not the triggering screen's ViewModel - see
     * `dev.jdtech.jellyfin.work.AutomaticSearchWorker`.
     */
    suspend fun awaitAutomaticSearchResult(episodeId: Int, commandId: Int): Result<AutomaticSearchOutcome>

    /**
     * Lists candidate releases for an episode (interactive/manual search). Results are cached per
     * episode for `AppPreferences.pvrReleaseCacheMinutes` (Settings > Integrations, see
     * [SonarrSearchRepositoryImpl]) since this call is expensive - Sonarr polls every enabled
     * indexer (directly or via a proxy like Prowlarr) before answering.
     */
    suspend fun getReleases(episodeId: Int): Result<List<PvrRelease>>

    /** Grabs a specific release returned by [getReleases]. */
    suspend fun grabRelease(release: PvrRelease): Result<Unit>
}
