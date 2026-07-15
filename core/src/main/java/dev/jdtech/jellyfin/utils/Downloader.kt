package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
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

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    fun getProgressFlow(downloadId: Long): Flow<DownloadProgress>
}
