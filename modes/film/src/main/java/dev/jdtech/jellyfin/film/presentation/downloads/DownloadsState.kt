package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.ManualImportCandidate
import dev.jdtech.jellyfin.models.PvrDiskSpaceResult
import dev.jdtech.jellyfin.models.PvrFetchError
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.utils.DeleteProgress
import dev.jdtech.jellyfin.utils.DeviceStorageStats
import dev.jdtech.jellyfin.utils.DownloadProgress
import java.util.UUID

data class DownloadsState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Exception? = null,
    val movies: List<FindroidMovie> = emptyList(),
    val showGroups: List<DownloadShowGroup> = emptyList(),
    val selectedIds: Set<UUID> = emptySet(),
    val downloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val deleteProgress: DeleteProgress? = null,
    val pvrQueueGroups: List<PvrQueueGroup> = emptyList(),
    val pvrErrors: List<PvrFetchError> = emptyList(),
    // (source, queueItemId) pairs, since Sonarr and Radarr each have their own queue-row id
    // namespace - a bare Int would collide between the two services.
    val selectedPvrQueueIds: Set<Pair<PvrSource, Int>> = emptySet(),
    val manualImport: ManualImportSheetState? = null,
    val diskSpace: PvrDiskSpaceResult = PvrDiskSpaceResult(),
    val deviceStorage: DeviceStorageStats? = null,
) {
    val isEmpty: Boolean
        get() = movies.isEmpty() && showGroups.isEmpty()
}

data class DownloadShowGroup(
    val seriesId: UUID,
    val seriesName: String,
    val episodes: List<FindroidEpisode>,
)

/**
 * A single Sonarr/Radarr queue entry as shown on the Downloads screen. [itemId]/[item] are only
 * non-null when [QueueStatusRepository][dev.jdtech.jellyfin.repository.QueueStatusRepository]
 * resolved this queue entry to an item in the Jellyfin server library. PVR-only rows retain
 * provider ids so they can open their Seerr detail when the PVR response identifies the item.
 */
data class PvrQueueUiItem(
    val itemId: UUID?,
    val title: String,
    val item: FindroidItem? = null,
    val posterUrl: String? = null,
    val tmdbId: Int? = null,
    val sonarrEpisodeId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val status: QueueStatus,
    // The PVR service's own queue-row id, needed to remove the entry (see
    // QueueStatusRepository.removeQueueItem).
    val queueItemId: Int = 0,
)

data class PvrQueueGroup(val source: PvrSource, val items: List<PvrQueueUiItem>)

/**
 * Drives the "manage imports" bottom sheet - the individual files inside a queue entry
 * Sonarr/Radarr couldn't fully auto-import (see [ManualImportCandidate]). [selectedIds] defaults
 * to every importable candidate once loaded; the user deselects files they don't want imported
 * (e.g. duplicates already on disk).
 */
data class ManualImportSheetState(
    val source: PvrSource,
    val downloadId: String,
    // The underlying queue row's own id - not [downloadId] - needed to reject the whole release
    // (remove + blocklist) rather than import it. See DownloadsViewModel.rejectManualImport.
    val queueItemId: Int,
    val title: String,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val isRejecting: Boolean = false,
    val candidates: List<ManualImportCandidate> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val error: String? = null,
)

/** One-shot feedback for a PVR queue-item removal, shown as a toast. */
sealed interface DownloadsEvent {
    data class PvrQueueItemRemoved(val title: String) : DownloadsEvent

    data class PvrQueueItemRemoveFailed(val message: String?) : DownloadsEvent

    /** [failed] is the count of the [removed] + [failed] total that didn't go through. */
    data class PvrQueueItemsRemoved(val removed: Int, val failed: Int) : DownloadsEvent

    data object ManualImportCompleted : DownloadsEvent

    data class ManualImportFailed(val message: String?) : DownloadsEvent
}
