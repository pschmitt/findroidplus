package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.SonarrEpisodeDto
import dev.jdtech.jellyfin.models.UpcomingEpisode

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
