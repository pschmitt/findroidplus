package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.models.UpcomingSeason

/**
 * Exposes Sonarr-known episodes/seasons of a specific show that aren't in the Jellyfin library
 * yet - what the Season screen shows as "upcoming" placeholder rows alongside real episodes, and
 * what the Show screen shows as placeholder season cards alongside real seasons. Unlike
 * [CalendarRepository] (a global date-range view across every show), this is scoped to one
 * series - matching it to a Sonarr series happens internally via tvdbId, the same join
 * [dev.jdtech.jellyfin.repository.matchSonarrCalendar] uses.
 */
interface SeasonEpisodesRepository {
    /**
     * [seriesTvdbId] identifies the show (from the matching
     * [dev.jdtech.jellyfin.models.FindroidShow.tvdbId]), [seasonNumber] the season, and
     * [knownEpisodeNumbers] the episode numbers already present in the Jellyfin library for that
     * season - anything Sonarr knows about beyond that set is returned. Empty (not an error) when
     * Sonarr isn't configured, the show isn't tracked by Sonarr, or nothing is missing.
     */
    suspend fun getUpcomingEpisodes(
        seriesTvdbId: String,
        seasonNumber: Int,
        knownEpisodeNumbers: Set<Int>,
    ): List<UpcomingEpisode>

    /**
     * The show-level equivalent of [getUpcomingEpisodes]: [seriesTvdbId] identifies the show and
     * [knownSeasonNumbers] the season numbers already present in the Jellyfin library for it -
     * any season Sonarr knows about beyond that set is returned. Empty (not an error) when Sonarr
     * isn't configured, the show isn't tracked by Sonarr, or nothing is missing.
     */
    suspend fun getMissingSeasons(
        seriesTvdbId: String,
        knownSeasonNumbers: Set<Int>,
    ): List<UpcomingSeason>
}
