package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.toFindroidSource
import timber.log.Timber

/** Deletes the local download (file + DB rows) for every item in [items], best-effort. */
suspend fun clearDownloads(
    items: List<FindroidItem>,
    database: ServerDatabaseDao,
    downloader: Downloader,
) {
    for (item in items) {
        try {
            val source =
                database.getSources(item.id).firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: continue
            downloader.deleteItem(item, source.toFindroidSource(database))
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete download for item ${item.id}")
        }
    }
}
