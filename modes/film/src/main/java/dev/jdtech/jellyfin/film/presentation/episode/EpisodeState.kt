package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.VideoMetadata
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope

data class EpisodeState(
    val episode: FindroidEpisode? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<FindroidItemPerson> = emptyList(),
    val dateFormat: String = "system",
    val existingScope: ExistingAutoDownloadScope = ExistingAutoDownloadScope(),
    val seriesTvdbId: String? = null,
    // Gates the search button - no point offering a Sonarr search that can only fail with a
    // toast when Sonarr isn't (fully) configured.
    val sonarrConfigured: Boolean = false,
    val releasePicker: ReleasePickerState? = null,
    val error: Exception? = null,
    // Mirrors AppPreferences.autoDeleteWatched/autoDeleteWatchedHours - see
    // DownloadsState.autoDeleteWatchedEnabled for the identical rationale (drives the "marked for
    // deletion" indicator and gates the exclude toggle, which is meaningless while the feature is
    // off).
    val autoDeleteWatchedEnabled: Boolean = false,
    val autoDeleteWatchedHours: Int = 24,
)
