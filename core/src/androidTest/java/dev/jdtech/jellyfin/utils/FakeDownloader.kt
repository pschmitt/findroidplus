package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText

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

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) = error("not used")

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) = error("not used")

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> = error("not used")
}
