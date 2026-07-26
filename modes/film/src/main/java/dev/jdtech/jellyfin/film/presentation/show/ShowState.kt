package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.UpcomingSeason
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope

data class ShowState(
    val show: FindroidShow? = null,
    val nextUp: FindroidEpisode? = null,
    val nextAiring: CalendarEntry? = null,
    val seasons: List<FindroidSeason> = emptyList(),
    // Sonarr-known seasons of this show not yet in the Jellyfin library - always empty unless
    // Sonarr is configured and the show is matched (see SeasonEpisodesRepository). Rendered as
    // dimmed placeholder cards after the real seasons, see ShowScreen.
    val missingSeasons: List<UpcomingSeason> = emptyList(),
    // Season numbers among [missingSeasons] that have a pending "download when available" request
    // queued (see PendingDownloadRequestDto) - drives the queued/not-queued icon state on
    // UpcomingSeasonCard.
    val queuedSeasonNumbers: Set<Int> = emptySet(),
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val autoDownloadEnabled: Boolean = false,
    val existingScope: ExistingAutoDownloadScope = ExistingAutoDownloadScope(),
    val hasDownloads: Boolean = false,
    val downloadsSizeBytes: Long = 0L,
    // The show's tvdb/tmdb ids, fetched once per loadShow() - tvdbId to resolve missingSeasons,
    // tmdbId to open the Seerr detail view when the user taps a missing-season placeholder.
    val seriesTvdbId: String? = null,
    val seriesTmdbId: Int? = null,
    // Gates the "also remove from Sonarr" cascade option on the delete-from-Jellyfin dialog - no
    // point offering it when Sonarr isn't (fully) configured.
    val sonarrConfigured: Boolean = false,
    // Drives the pull-to-refresh spinner - separate from `show == null` (first load, full-screen
    // spinner instead) since a refresh keeps showing the existing content underneath.
    val isRefreshing: Boolean = false,
    val error: Exception? = null,
)
