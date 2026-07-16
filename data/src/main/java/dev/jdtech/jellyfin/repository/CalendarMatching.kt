package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrCalendarEntry
import dev.jdtech.jellyfin.api.pvr.SonarrCalendarEntry
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.PvrSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Pure functions matching Sonarr/Radarr calendar entries to Jellyfin item ids - no suspend, no
 * I/O, so they're directly unit-testable without Room/Hilt/Android in the loop.
 *
 * Unlike `QueueStatusMatching.kt`, an entry Findroid can't match to a local Jellyfin item is never
 * dropped here - it's still returned with `itemId = null`, since the calendar's whole point is to
 * surface "something is coming" even before the item exists in the Jellyfin library. Because of
 * this, both functions map every input entry to at most one output entry (they can only skip an
 * entry when it has no usable date at all, which shouldn't happen given the server-side date-range
 * filter but is handled defensively).
 */

/** Sonarr's `series.tvdbId`/Radarr's `tmdbId` default to 0 when the field is absent from the DTO. */
private const val UNSET_PROVIDER_ID = 0

fun matchSonarrCalendar(
    entries: List<SonarrCalendarEntry>,
    jellyfinShows: List<FindroidShow>,
): List<CalendarEntry> {
    val showByTvdbId: Map<String, FindroidShow> =
        jellyfinShows.mapNotNull { show -> show.tvdbId?.let { it to show } }.toMap()

    return entries.mapNotNull { entry ->
        val date = entry.airDateUtc?.let(::parseFlexibleDate) ?: return@mapNotNull null
        val tvdbId = entry.series?.tvdbId?.takeIf { it != UNSET_PROVIDER_ID }
        val show = tvdbId?.let { showByTvdbId[it.toString()] }
        // Falls back to the series name from Sonarr's payload when there's no Jellyfin match,
        // but never to entry.title (the episode's own title) - that belongs in the subtitle
        // (see buildEpisodeSubtitle below), not the show-title slot.
        val title = show?.name ?: entry.series?.title?.takeIf { it.isNotBlank() } ?: ""
        // Tapping a calendar entry should land on the season (where the episode itself lives),
        // not the show's overview page - see SeasonScreen's own episode list. Falls back to the
        // show's own images if the matching season isn't found (e.g. season not yet in Jellyfin).
        val season = show?.seasons?.firstOrNull { it.indexNumber == entry.seasonNumber }

        CalendarEntry(
            date = date,
            source = PvrSource.SONARR,
            title = title,
            subtitle = buildEpisodeSubtitle(entry.seasonNumber, entry.episodeNumber, entry.title),
            itemId = season?.id,
            hasFile = entry.hasFile,
            monitored = entry.monitored,
            images = season?.images ?: show?.images,
            episodeId = entry.id,
        )
    }
}

fun matchRadarrCalendar(
    entries: List<RadarrCalendarEntry>,
    jellyfinMovies: List<FindroidMovie>,
    start: LocalDate,
    end: LocalDate,
): List<CalendarEntry> {
    val movieByTmdbId: Map<String, FindroidMovie> =
        jellyfinMovies.mapNotNull { movie -> movie.tmdbId?.let { it to movie } }.toMap()

    return entries.mapNotNull { entry ->
        val date = selectRadarrDate(entry, start, end) ?: return@mapNotNull null
        val tmdbId = entry.tmdbId.takeIf { it != UNSET_PROVIDER_ID }
        val movie = tmdbId?.let { movieByTmdbId[it.toString()] }

        CalendarEntry(
            date = date,
            source = PvrSource.RADARR,
            title = movie?.name ?: entry.title,
            subtitle = null,
            itemId = movie?.id,
            hasFile = entry.hasFile,
            monitored = entry.monitored,
            images = movie?.images,
            movieId = entry.id,
        )
    }
}

/**
 * Radarr's calendar entry can carry up to three release dates. Preference order (matching the
 * order these fields are declared/queried elsewhere for Radarr): `digitalRelease`,
 * `physicalRelease`, `inCinemas`. The earliest of these that falls within the requested
 * `[start, end]` window wins (Radarr's calendar endpoint already server-side filters to "any of
 * these in range", so this is just picking which one to display). If none of the non-null dates
 * fall in range - which shouldn't happen given that filter, but is handled defensively - falls
 * back to the first non-null date in preference order. Returns `null` only when all three fields
 * are null/unparseable.
 */
internal fun selectRadarrDate(entry: RadarrCalendarEntry, start: LocalDate, end: LocalDate): LocalDate? {
    val candidates =
        listOfNotNull(
            entry.digitalRelease?.let(::parseFlexibleDate),
            entry.physicalRelease?.let(::parseFlexibleDate),
            entry.inCinemas?.let(::parseFlexibleDate),
        )
    if (candidates.isEmpty()) return null
    return candidates.filter { it in start..end }.minOrNull() ?: candidates.first()
}

internal fun buildEpisodeSubtitle(seasonNumber: Int, episodeNumber: Int, title: String?): String {
    val episodeTitle = title?.takeIf { it.isNotBlank() }
    return if (episodeTitle != null) {
        "S%02dE%02d - %s".format(seasonNumber, episodeNumber, episodeTitle)
    } else {
        "S%02dE%02d".format(seasonNumber, episodeNumber)
    }
}

/**
 * Sonarr/Radarr calendar date fields are inconsistent in shape across endpoints/versions - plain
 * dates (`"2024-07-24"`, e.g. some `inCinemas` values) and full UTC instants (`"2024-07-24T01:00:00Z"`,
 * e.g. `airDateUtc`) both appear. Tries a plain [LocalDate] first, then falls back to parsing as
 * an [Instant] converted to the system default time zone's local date. Returns `null` if neither
 * parses, rather than throwing - a single malformed date from the PVR side must never take down
 * the whole calendar fetch.
 */
internal fun parseFlexibleDate(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        try {
            Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (e2: DateTimeParseException) {
            null
        }
    }
