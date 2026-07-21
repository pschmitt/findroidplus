package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.SonarrEpisodeDto
import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.models.UpcomingSeason

/**
 * Pure function - no suspend, no I/O - so it's directly unit-testable without Room/Hilt/Android
 * in the loop, same as [matchSonarrCalendar]/[matchRadarrCalendar].
 */
fun matchUpcomingEpisodes(
    sonarrEpisodes: List<SonarrEpisodeDto>,
    seasonNumber: Int,
    knownEpisodeNumbers: Set<Int>,
): List<UpcomingEpisode> =
    sonarrEpisodes
        .filter { it.seasonNumber == seasonNumber && it.episodeNumber !in knownEpisodeNumbers }
        .map { episode ->
            UpcomingEpisode(
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                title = episode.title?.takeIf { it.isNotBlank() },
                airDate = episode.airDateUtc?.let(::parseFlexibleDate),
                airTime = episode.airDateUtc?.let(::parseLocalTime),
                hasFile = episode.hasFile,
                monitored = episode.monitored,
                episodeId = episode.id,
            )
        }
        .sortedBy { it.episodeNumber }

/**
 * Pure function, same rationale as [matchUpcomingEpisodes] - the show-level equivalent, used by
 * the Show screen to surface seasons Sonarr knows about that have no matching [FindroidSeason] in
 * the Jellyfin library yet. Season 0 ("Specials" in Sonarr's convention) is deliberately excluded:
 * it exists for virtually every series and is usually unmonitored, so surfacing it as a
 * requestable placeholder on every show would be noise rather than signal.
 */
fun matchMissingSeasons(
    sonarrEpisodes: List<SonarrEpisodeDto>,
    knownSeasonNumbers: Set<Int>,
): List<UpcomingSeason> =
    sonarrEpisodes
        .filter { it.seasonNumber > 0 && it.seasonNumber !in knownSeasonNumbers }
        .groupBy { it.seasonNumber }
        .map { (seasonNumber, episodes) ->
            UpcomingSeason(
                seasonNumber = seasonNumber,
                episodeCount = episodes.size,
                monitored = episodes.any { it.monitored },
            )
        }
        .sortedBy { it.seasonNumber }
