package dev.pschmitt.jellyfin.models

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * A single upcoming Sonarr/Radarr release, already date-resolved and (best-effort) matched
 * against a real Jellyfin item id by [dev.pschmitt.jellyfin.repository.CalendarRepository] - see
 * `matchSonarrCalendar`/`matchRadarrCalendar` in `CalendarMatching.kt`.
 *
 * Unlike [QueueStatus], an entry Findroid couldn't match to a local Jellyfin item is still
 * included here (with [itemId] `null`) rather than dropped - the point of the calendar is to show
 * "something is coming" even before the show/movie has been added to the Jellyfin library.
 */
/** See [dev.pschmitt.jellyfin.repository.CalendarRepository.getUpcoming]. */
data class CalendarResult(
    val entries: List<CalendarEntry> = emptyList(),
    val errors: List<PvrFetchError> = emptyList(),
)

data class CalendarEntry(
    val date: LocalDate,
    // Exact air time in the device's local time zone - only known when the PVR side supplied a
    // full instant (Sonarr's airDateUtc); null for date-only values (Radarr release dates).
    val airTime: LocalTime? = null,
    val source: PvrSource,
    val title: String,
    val subtitle: String?,
    val itemId: UUID?,
    val episodeItemId: UUID? = null,
    val hasFile: Boolean,
    val monitored: Boolean,
    // Carried over from the matched FindroidShow/FindroidMovie at match time (see
    // matchSonarrCalendar/matchRadarrCalendar in CalendarMatching.kt) - null for unmatched
    // entries.
    val images: FindroidImages? = null,
    /** Seerr or PVR artwork for entries not yet present in Jellyfin. */
    val posterUrl: String? = null,
    // Sonarr's own numeric episode id (Sonarr's /calendar endpoint returns episode resources) -
    // always null for Radarr entries. Already known here, so triggering a search from the
    // Calendar screen doesn't need a separate SonarrSearchRepository.resolveEpisodeId round trip.
    val episodeId: Int? = null,
    /** Sonarr's season/episode coordinates, retained for Seerr's episode detail route. */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    // Radarr's own numeric movie id (Radarr's /calendar endpoint returns movie resources) -
    // always null for Sonarr entries. Same rationale as [episodeId], for
    // RadarrSearchRepository-backed searches.
    val movieId: Int? = null,
    // TMDB id of the movie (Radarr) or series (Sonarr v4) - lets unmatched entries (not in the
    // Jellyfin library yet) open the Seerr media detail view instead of being dead ends.
    val tmdbId: Int? = null,
)
