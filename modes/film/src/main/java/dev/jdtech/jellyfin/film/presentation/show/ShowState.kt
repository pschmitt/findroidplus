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
    val error: Exception? = null,
)
