package dev.pschmitt.jellyfin.utils

import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.FindroidSourceType
import dev.pschmitt.jellyfin.models.toFindroidSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Deletes the local download (file + DB rows) for every item in [items], best-effort. */
suspend fun clearDownloads(
    items: List<FindroidItem>,
    database: ServerDatabaseDao,
    downloader: Downloader,
) {
    // File deletion below is blocking I/O, so run it off the caller's dispatcher - otherwise a
    // large batch delete janks the UI since callers typically invoke this from viewModelScope
    // (Dispatchers.Main.immediate).
    withContext(Dispatchers.IO) {
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
}
