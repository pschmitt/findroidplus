package dev.jdtech.jellyfin.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.resolveDownloadStorageIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Moves or deletes already-downloaded files when the user switches the download location
 * preference between internal/external storage. Storage indices are re-resolved from the
 * (raw "internal"/"external") input strings at run time via [resolveDownloadStorageIndex] rather
 * than passed in directly, so a stale index can't be enqueued if volumes change between the
 * settings screen and this worker actually running.
 */
@HiltWorker
class RelocateDownloadsWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: Downloader,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val mode = inputData.getString(KEY_MODE) ?: return@withContext Result.failure()
            val fromLocation =
                inputData.getString(KEY_FROM_LOCATION) ?: return@withContext Result.failure()
            val fromIndex = resolveDownloadStorageIndex(applicationContext, fromLocation)
            if (fromIndex < 0) return@withContext Result.failure()

            NotificationChannels.ensureDownloads(applicationContext)

            try {
                setForeground(foregroundInfo(progressNotification(mode, 0, 0)))
                setProgress(workDataOf(KEY_MODE to mode, KEY_DONE to 0, KEY_TOTAL to 0))

                when (mode) {
                    MODE_MOVE -> {
                        val toLocation =
                            inputData.getString(KEY_TO_LOCATION)
                                ?: return@withContext Result.failure()
                        val toIndex = resolveDownloadStorageIndex(applicationContext, toLocation)
                        if (toIndex < 0) return@withContext Result.failure()
                        downloader.moveDownloads(fromIndex, toIndex) { done, total ->
                            notify(progressNotification(mode, done, total))
                            setProgress(workDataOf(KEY_MODE to mode, KEY_DONE to done, KEY_TOTAL to total))
                        }
                    }
                    MODE_CLEAR -> {
                        downloader.clearDownloads(fromIndex) { done, total ->
                            notify(progressNotification(mode, done, total))
                            setProgress(workDataOf(KEY_MODE to mode, KEY_DONE to done, KEY_TOTAL to total))
                        }
                    }
                    else -> return@withContext Result.failure()
                }

                notify(completeNotification())
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Failed to relocate downloads (mode=$mode)")
                notify(failedNotification())
                Result.failure()
            }
        }

    private fun progressNotification(mode: String, done: Int, total: Int): Notification {
        val title =
            applicationContext.getString(
                if (mode == MODE_CLEAR) {
                    CoreR.string.relocate_downloads_notification_clearing
                } else {
                    CoreR.string.relocate_downloads_notification_moving
                }
            )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsContentIntent(applicationContext))
            .apply {
                if (total > 0) setProgress(total, done, false) else setProgress(0, 0, true)
            }
            .build()
    }

    private fun completeNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(
                applicationContext.getString(CoreR.string.relocate_downloads_notification_done)
            )
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsContentIntent(applicationContext))
            .build()
    }

    private fun failedNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.download_failed))
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsContentIntent(applicationContext))
            .build()
    }

    private fun notify(notification: Notification) {
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MODE = "KEY_MODE"
        const val KEY_FROM_LOCATION = "KEY_FROM_LOCATION"
        const val KEY_TO_LOCATION = "KEY_TO_LOCATION"
        const val KEY_DONE = "KEY_DONE"
        const val KEY_TOTAL = "KEY_TOTAL"
        const val MODE_MOVE = "MOVE"
        const val MODE_CLEAR = "CLEAR"

        private const val CHANNEL_ID = NotificationChannels.DOWNLOADS
        private const val NOTIFICATION_ID = 279_412_002
    }
}
