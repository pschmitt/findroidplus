package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope

data class ShowState(
    val show: FindroidShow? = null,
    val nextUp: FindroidEpisode? = null,
    val nextAiring: CalendarEntry? = null,
    val seasons: List<FindroidSeason> = emptyList(),
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val autoDownloadEnabled: Boolean = false,
    val existingScope: ExistingAutoDownloadScope = ExistingAutoDownloadScope(),
    val hasDownloads: Boolean = false,
    val downloadsSizeBytes: Long = 0L,
    val error: Exception? = null,
)
