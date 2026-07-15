package dev.jdtech.jellyfin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Cancel action on the download progress notification, see VideoDownloadWorker. */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject lateinit var downloader: Downloader
    @Inject lateinit var database: ServerDatabaseDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PAUSE_ALL) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DownloadNotificationCoordinator.activeDownloadIds().forEach { downloadId ->
                        val sourceId = database.getSourceByDownloadId(downloadId)?.id
                        downloader.pauseDownload(downloadId)
                        if (sourceId != null) {
                            DownloadNotificationCoordinator.remove(context, sourceId)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return
        if (intent.action != ACTION_CANCEL && intent.action != ACTION_PAUSE) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Capture the sourceId before cancelDownload() deletes the row it lives on - the
                // notification coordinator is keyed by sourceId, not downloadId.
                val sourceId = database.getSourceByDownloadId(downloadId)?.id
                if (intent.action == ACTION_CANCEL) {
                    downloader.cancelDownload(downloadId)
                } else {
                    downloader.pauseDownload(downloadId)
                }
                // Both actions cancel the WorkManager job, which races a CancellationException
                // past doWork()'s own IOException catch (it never runs there), so this entry has
                // to be cleared here instead - this also correctly leaves the shared notification
                // up if other downloads are still active.
                if (sourceId != null) {
                    DownloadNotificationCoordinator.remove(context, sourceId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "dev.jdtech.jellyfin.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE = "dev.jdtech.jellyfin.action.PAUSE_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "dev.jdtech.jellyfin.action.PAUSE_ALL_DOWNLOADS"
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
