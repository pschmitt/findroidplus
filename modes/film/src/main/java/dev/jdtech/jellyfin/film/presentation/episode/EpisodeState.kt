package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.models.QueueStatus
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
    // This episode's own Sonarr queue entry, if any - drives the download widget's "there's an
    // import issue, tap to resolve" affordance. Mirrors MovieState.queueStatus.
    val queueStatus: QueueStatus? = null,
    // Every entry in this episode's own duplicate cluster (see PvrQueueEntry.duplicateGroupKey) -
    // unlike [queueStatus], which is already collapsed to one. Mirrors MovieState.queueEntries.
    val queueEntries: List<PvrQueueEntry> = emptyList(),
    // Whether the current Jellyfin user's policy allows deleting media at all - gates whether
    // "Delete from Jellyfin" is shown in the overflow menu, rather than showing it and having the
    // delete fail with a permissions error.
    val canDelete: Boolean = false,
    val releasePicker: ReleasePickerState? = null,
    val error: Exception? = null,
    // Mirrors AppPreferences.autoDeleteWatched/autoDeleteWatchedHours - see
    // DownloadsState.autoDeleteWatchedEnabled for the identical rationale (drives the "marked for
    // deletion" indicator and gates the exclude toggle, which is meaningless while the feature is
    // off).
    val autoDeleteWatchedEnabled: Boolean = false,
    val autoDeleteWatchedHours: Int = 24,
    // Drives the pull-to-refresh spinner - separate from `episode == null` (first load, full-
    // screen spinner instead) since a refresh keeps showing the existing content underneath.
    val isRefreshing: Boolean = false,
)
