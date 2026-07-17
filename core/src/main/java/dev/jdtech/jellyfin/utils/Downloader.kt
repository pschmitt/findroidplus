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

    // Total/available bytes on storageIndex's volume (see downloadItem's storageIndex), for a
    // storage-usage summary UI. Null if that volume isn't mounted.
    fun getStorageStats(storageIndex: Int = 0): DeviceStorageStats?
}
