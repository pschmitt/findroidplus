package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.ManualImportCandidate
import dev.jdtech.jellyfin.models.PvrDiskSpaceResult
import dev.jdtech.jellyfin.models.PvrFetchError
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.utils.DeleteProgress
import dev.jdtech.jellyfin.utils.DeviceStorageStats
import dev.jdtech.jellyfin.utils.DownloadProgress
import dev.jdtech.jellyfin.utils.MigrateProgress
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
    val moveProgress: MigrateProgress? = null,
    // Items included in the migrate batch(es) currently in flight - MigrateDownloadsWorker only
    // reports an aggregate done/total count (see moveProgress), not which specific item it's on,
    // so every id in a batch is marked "moving" for that batch's whole duration rather than
    // pinpointing the exact one being copied right now.
    val migratingIds: Set<UUID> = emptySet(),
    val pvrQueueGroups: List<PvrQueueGroup> = emptyList(),
    val pvrErrors: List<PvrFetchError> = emptyList(),
    // Services still waiting on their first-ever successful poll this session (see
    // PvrQueueSnapshot.pendingSources) - drives a "loading" placeholder instead of either
    // silently showing nothing or jumping straight to an error banner.
    val pvrPendingSources: Set<PvrSource> = emptySet(),
    // (source, queueItemId) pairs, since Sonarr and Radarr each have their own queue-row id
    // namespace - a bare Int would collide between the two services.
    val selectedPvrQueueIds: Set<Pair<PvrSource, Int>> = emptySet(),
    val diskSpace: PvrDiskSpaceResult = PvrDiskSpaceResult(),
    // Every mounted app-storage volume (internal, plus external/removable if present) - see
    // Downloader.getAllStorageStats(). Not just one: a device with an SD card configured as the
    // download location needs its usage shown there, not folded into (or worse, mistaken for)
    // internal storage.
    val deviceStorages: List<DeviceStorageStats> = emptyList(),
    // Mirrors AppPreferences.autoDeleteWatched/autoDeleteWatchedHours - read once per refresh so
    // DownloadRow can compute FindroidEpisode.isMarkedForAutoDeletion() without injecting
    // AppPreferences into the Compose layer, and so the "keep" pin toggle only renders while the
    // feature is actually on (nothing to protect against otherwise).
    val autoDeleteWatchedEnabled: Boolean = false,
    val autoDeleteWatchedHours: Int = 24,
    // Mirrors AppPreferences.maxDownloadSizeEnabled/maxDownloadSizeGb - read once per refresh so
    // the screen can show a warning once the total downloaded size crosses the cap. Only gates
    // automatic downloads (see AutoDownloadRuleEvaluator/PendingDownloadFulfiller); this is purely
    // informational here.
    val maxDownloadSizeEnabled: Boolean = false,
    val maxDownloadSizeGb: Int = 20,
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
    // The episode's own title (e.g. "The Red Dragon and the Gold"), distinct from [title], which
    // is "Series - S3E6"-shaped. Only available once matched to a Jellyfin FindroidEpisode -
    // Sonarr/Radarr's queue APIs don't return an episode title for unmatched entries, and movies
    // have no separate subtitle concept at all (their title already is the movie title).
    val subtitle: String? = null,
    val item: FindroidItem? = null,
    val posterUrl: String? = null,
    val tmdbId: Int? = null,
    val sonarrEpisodeId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val status: QueueStatus,
    // The PVR service's own queue-row id, needed to remove the entry (see
    // QueueStatusRepository.removeQueueItem). Belongs to the entry this row's own display fields
    // (title/poster/status) were taken from - see [duplicates] for the rest of its cluster.
    val queueItemId: Int = 0,
    // Every entry in this row's duplicate cluster (see PvrQueueEntry.duplicateGroupKey),
    // including the one this row displays - a single-element list in the overwhelmingly common
    // case of no duplicates. Carried here so opening the manage-import sheet can seed every
    // duplicate's candidates without a second repository round-trip.
    val duplicates: List<PvrQueueEntry> = emptyList(),
)

data class PvrQueueGroup(val source: PvrSource, val items: List<PvrQueueUiItem>)

/**
 * One underlying queue entry's manage-import state inside [ManualImportSheetState] - normally
 * there's exactly one, but a duplicate cluster (see PvrQueueEntry.duplicateGroupKey) seeds one
 * per entry so the user can pick which release to actually import from.
 */
data class ManualImportEntry(
    val source: PvrSource,
    val downloadId: String,
    // The underlying queue row's own id - not [downloadId] - needed to remove this entry (either
    // as the losing duplicate after a different entry's import, or as part of rejecting the
    // whole cluster).
    val queueItemId: Int,
    val isLoading: Boolean = true,
    val candidates: List<ManualImportCandidate> = emptyList(),
    // Defaults to every importable candidate once loaded; the user deselects files they don't
    // want imported (e.g. duplicates already on disk).
    val selectedIds: Set<Int> = emptySet(),
    val error: String? = null,
)

/**
 * Drives the "manage imports" bottom sheet. [entries] holds one [ManualImportEntry] per
 * underlying queue row in the cluster - size 1 in the overwhelmingly common case (nothing to pick
 * between), 2+ when duplicate grabs of the same release are both still awaiting import.
 * [selectedEntryIndex] is which one the user is currently reviewing/importing from; confirming
 * imports from that entry only and then removes every other entry in [entries] as a losing
 * duplicate - see ManualImportController.confirm.
 */
data class ManualImportSheetState(
    val title: String,
    val entries: List<ManualImportEntry>,
    val selectedEntryIndex: Int = 0,
    val isImporting: Boolean = false,
    val isRejecting: Boolean = false,
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
