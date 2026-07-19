package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface Downloader {
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    // Looks the item/source up via the DB itself rather than taking a FindroidItem, so callers
    // that only have a downloadId (e.g. a notification action BroadcastReceiver) can cancel
    // without having to reconstruct a FindroidItem first.
    suspend fun cancelDownload(downloadId: Long)

    // "Pausing" is just cancelling the WorkManager job without running deleteItem() - the partial
    // .download file and the sources DB row are left in place, so VideoDownloadWorker's Range-based
    // resume logic picks the file back up the next time this source is enqueued.
    suspend fun pauseDownload(downloadId: Long)

    suspend fun resumeDownload(downloadId: Long): UiText?

    // Jumps a queued download to the front of the line, pausing one other running download to
    // make room for it. No-op if downloadId isn't currently queued (e.g. it already started).
    suspend fun forceDownload(downloadId: Long)

    // Moves every id in downloadIds to the front of the queue (in list order) and force-starts
    // the first one, without pausing more than one unrelated running download. Used to
    // prioritize a whole show's queued episodes over other shows'.
    suspend fun forceDownloadGroup(downloadIds: List<Long>)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    fun getProgressFlow(downloadId: Long): Flow<DownloadProgress>

    // Copies every downloaded (LOCAL) source and its external media streams currently under
    // fromStorageIndex's volume over to toStorageIndex's volume, repointing the DB rows at the
    // new path once each file is verified copied. Used when the user switches the download
    // location preference between internal/external storage and chooses to relocate existing
    // downloads rather than leave them behind or delete them.
    suspend fun moveDownloads(
        fromStorageIndex: Int,
        toStorageIndex: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    )

    // Deletes every downloaded (LOCAL) source currently under fromStorageIndex's volume via the
    // same cascade as deleteItem(), so those items simply fall back to streaming.
    suspend fun clearDownloads(
        fromStorageIndex: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    )

    // Enqueues a background deletion of every item in itemIds (movies and/or episodes), via the
    // same deleteItem() cascade, so the Downloads page's single/bulk/clear-all delete actions
    // survive the app being backgrounded and show progress instead of blocking silently. Multiple
    // calls are processed sequentially (one batch after another), not concurrently.
    suspend fun deleteItems(itemIds: List<UUID>)

    // Emits the progress of the currently running (or most recently finished) deleteItems() batch,
    // or null if none has run yet / the last one fully drained. Backed by WorkManager's unique
    // work info for the shared "deleteDownloads" work chain.
    fun getDeleteProgressFlow(): Flow<DeleteProgress?>

    // Stats for every mounted app-storage volume (internal, plus any external/removable SD
    // card/USB storage), in getExternalFilesDirs() order - for a storage-usage summary UI that
    // covers everywhere a download could actually live, not just index 0. A volume that fails to
    // read (unmounted, StatFs error) is simply omitted rather than the whole list failing.
    fun getAllStorageStats(): List<DeviceStorageStats>

    // Resolves the user's configured "download location" preference (internal/external/ask) to a
    // concrete getExternalFilesDirs() index, falling back to 0 when the preference is "ask" or
    // its preferred volume isn't currently mounted - there's no one to ask in a non-interactive
    // context (a bulk/auto-download batch, or the background AutoDownloadWorker), so this is the
    // storageIndex every such caller should use instead of hardcoding 0 regardless of preference.
    fun resolvePreferredStorageIndex(): Int

    // Moves the LOCAL sources of exactly these items to toStorageIndex's volume (a source already
    // there is left alone) - the selection-scoped counterpart to moveDownloads()'s whole-volume
    // move, called directly by MigrateDownloadsWorker. [onProgress] reports (done, total) as each
    // item finishes. Prefer migrateItems() from UI code - this is the piece the worker runs.
    suspend fun moveItems(
        itemIds: List<UUID>,
        toStorageIndex: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    )

    // Enqueues a background migration of exactly these items (e.g. the Downloads screen's
    // selection) to toStorageIndex's volume, via the same "survives the app being backgrounded"
    // WorkManager pattern as deleteItems().
    suspend fun migrateItems(itemIds: List<UUID>, toStorageIndex: Int)

    // Emits the progress of the currently running (or most recently finished) migrateItems()
    // batch, or null if none has run yet / the last one fully drained - mirrors
    // getDeleteProgressFlow().
    fun getMigrateProgressFlow(): Flow<MigrateProgress?>
}
