package dev.pschmitt.jellyfin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles the "Download" action on a new-item notification (see [NewItemNotifier]). The
 * notification only carries an item id + movie/episode flag, not a full
 * [dev.pschmitt.jellyfin.models.FindroidItem] (Intent extras aren't the place for that), so this
 * re-fetches the item from the server before downloading it - the same round trip the app would do
 * opening the item's own detail page and tapping Download there.
 */
@AndroidEntryPoint
class NewItemDownloadActionReceiver : BroadcastReceiver() {

    @Inject lateinit var downloader: Downloader
    @Inject lateinit var repository: JellyfinRepository
    @Inject lateinit var database: ServerDatabaseDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD) return
        val itemId =
            intent.getStringExtra(EXTRA_ITEM_ID)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Already downloaded/queued by the time the user taps this (e.g. an
                // auto-download rule beat them to it) - nothing left to do.
                if (database.getSources(itemId).isEmpty()) {
                    val item =
                        if (isMovie) repository.getMovie(itemId) else repository.getEpisode(itemId)
                    val sourceId = item.sources.firstOrNull()?.id
                    if (sourceId != null) {
                        downloader.downloadItem(
                            item,
                            sourceId,
                            storageIndex = downloader.resolvePreferredStorageIndex(),
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download new item $itemId from notification action")
            } finally {
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "dev.pschmitt.jellyfin.action.DOWNLOAD_NEW_ITEM"
        const val EXTRA_ITEM_ID = "EXTRA_ITEM_ID"
        const val EXTRA_IS_MOVIE = "EXTRA_IS_MOVIE"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
