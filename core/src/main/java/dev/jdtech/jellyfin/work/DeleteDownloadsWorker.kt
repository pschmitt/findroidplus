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
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Deletes a batch of already-downloaded movies/episodes in the background, so the Downloads
 * page's single/bulk/clear-all delete actions survive the app being backgrounded and show
 * progress via a notification instead of silently blocking on [Downloader.deleteItem] calls in
 * the calling ViewModel's scope.
 */
@HiltWorker
class DeleteDownloadsWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val downloader: Downloader,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val itemIds =
                inputData.getStringArray(KEY_ITEM_IDS)?.map { UUID.fromString(it) }
                    ?: return@withContext Result.failure()
            if (itemIds.isEmpty()) return@withContext Result.success()

            NotificationChannels.ensureDownloads(applicationContext)

            try {
                setForeground(foregroundInfo(progressNotification(0, itemIds.size)))

                itemIds.forEachIndexed { index, itemId ->
                    try {
                        val item = findFindroidItem(itemId)
                        val source =
                            item?.let {
                                database.getSources(itemId).firstOrNull {
                                    it.type == FindroidSourceType.LOCAL
                                }
                            }
                        if (item != null && source != null) {
                            downloader.deleteItem(item, source.toFindroidSource(database))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to delete download for item $itemId")
                    }
                    val done = index + 1
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to itemIds.size))
                    notify(progressNotification(done, itemIds.size))
                }

                notify(completeNotification())
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete downloads batch")
                Result.failure()
            }
        }

    private fun findFindroidItem(itemId: UUID): FindroidItem? {
        val userId = jellyfinRepository.getUserId()
        return try {
            database.getMovie(itemId).toFindroidMovie(database, userId)
        } catch (_: Exception) {
            try {
                database.getEpisode(itemId).toFindroidEpisode(database, userId)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun progressNotification(done: Int, total: Int): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(
                applicationContext.getString(CoreR.string.delete_downloads_notification_deleting)
            )
            .setSmallIcon(CoreR.drawable.ic_trash)
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
                applicationContext.getString(CoreR.string.delete_downloads_notification_done)
            )
            .setSmallIcon(CoreR.drawable.ic_trash)
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
        const val KEY_DONE = "KEY_DONE"
        const val KEY_TOTAL = "KEY_TOTAL"

        private const val CHANNEL_ID = NotificationChannels.DOWNLOADS
        private const val NOTIFICATION_ID = 279_412_003
    }
}
