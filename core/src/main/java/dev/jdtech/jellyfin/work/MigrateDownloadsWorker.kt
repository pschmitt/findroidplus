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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Moves a specific selection of already-downloaded items (the Downloads screen's "migrate
 * selected" action) to a different storage volume - the selection-scoped counterpart to
 * [RelocateDownloadsWorker], which moves *every* download on one volume when the user changes the
 * download-location preference in Settings. Runs as a foreground service for the same reason: a
 * move can involve copying several large files, easily long enough to outlast the screen that
 * started it.
 */
@HiltWorker
class MigrateDownloadsWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: Downloader,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val itemIds =
                inputData.getStringArray(KEY_ITEM_IDS)?.map { UUID.fromString(it) }
                    ?: return@withContext Result.failure()
            val toStorageIndex = inputData.getInt(KEY_TO_STORAGE_INDEX, -1)
            if (toStorageIndex < 0) return@withContext Result.failure()
            if (itemIds.isEmpty()) return@withContext Result.success()

            NotificationChannels.ensureDownloads(applicationContext)

            try {
                setForeground(foregroundInfo(progressNotification(0, itemIds.size)))
                setProgress(workDataOf(KEY_DONE to 0, KEY_TOTAL to itemIds.size))

                downloader.moveItems(itemIds, toStorageIndex) { done, total ->
                    notify(progressNotification(done, total))
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                }

                notify(completeNotification())
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Failed to migrate downloads to storage index $toStorageIndex")
                notify(failedNotification())
                Result.failure()
            }
        }

    private fun progressNotification(done: Int, total: Int): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(
                applicationContext.getString(CoreR.string.relocate_downloads_notification_moving)
            )
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
        const val KEY_ITEM_IDS = "KEY_ITEM_IDS"
        const val KEY_TO_STORAGE_INDEX = "KEY_TO_STORAGE_INDEX"
        const val KEY_DONE = "KEY_DONE"
        const val KEY_TOTAL = "KEY_TOTAL"

        private const val CHANNEL_ID = NotificationChannels.DOWNLOADS
        private const val NOTIFICATION_ID = 279_412_004
    }
}
