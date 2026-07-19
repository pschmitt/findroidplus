package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class FakeDownloader : Downloader {
    val downloadedItemIds = mutableListOf<String>()

    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> {
        downloadedItemIds.add(item.id.toString())
        return Pair(1L, null)
    }

    override suspend fun cancelDownload(downloadId: Long) = error("not used")

    override suspend fun pauseDownload(downloadId: Long) = error("not used")

    override suspend fun resumeDownload(downloadId: Long): UiText? = error("not used")

    override suspend fun forceDownload(downloadId: Long) = error("not used")

    override suspend fun forceDownloadGroup(downloadIds: List<Long>) = error("not used")

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) = error("not used")

    override fun getProgressFlow(downloadId: Long): Flow<DownloadProgress> = error("not used")

    override suspend fun moveDownloads(
        fromStorageIndex: Int,
        toStorageIndex: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ) = error("not used")

    override suspend fun clearDownloads(
        fromStorageIndex: Int,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ) = error("not used")

    override suspend fun deleteItems(itemIds: List<UUID>) = error("not used")

    override fun getDeleteProgressFlow(): Flow<DeleteProgress?> = error("not used")

    override fun getAllStorageStats(): List<DeviceStorageStats> = error("not used")

    override fun resolvePreferredStorageIndex(): Int = error("not used")
}
