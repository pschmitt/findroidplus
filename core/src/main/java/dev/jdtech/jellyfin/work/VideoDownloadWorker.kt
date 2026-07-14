package dev.jdtech.jellyfin.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Streams the primary media source to disk in fixed-size chunks under our own control, instead of
 * delegating to the system DownloadManager. DownloadManager is the actual root cause of the
 * >4GiB failure - see DownloaderImpl for the full writeup. Byte counts here are Long end-to-end.
 */
@HiltWorker
class VideoDownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val sourceId =
                inputData.getString(KEY_SOURCE_ID) ?: return@withContext Result.failure()
            val sourceUrl =
                inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
            val destinationPath =
                inputData.getString(KEY_DESTINATION_PATH) ?: return@withContext Result.failure()
            val finalPath =
                inputData.getString(KEY_FINAL_PATH) ?: return@withContext Result.failure()
            val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, -1L)
            val itemName = inputData.getString(KEY_ITEM_NAME) ?: sourceId
            val notificationId = sourceId.hashCode()

            createNotificationChannel()

            try {
                setForeground(foregroundInfo(notificationId, progressNotification(itemName, 0, true)))

                downloadToFile(sourceUrl, destinationPath, expectedSize, notificationId, itemName)

                val destFile = File(destinationPath)
                val finalFile = File(finalPath)
                if (!destFile.renameTo(finalFile)) {
                    throw IOException("Failed to rename $destinationPath to $finalPath")
                }
                database.setSourcePath(sourceId, finalPath)

                notify(notificationId, completeNotification(itemName))
                Result.success()
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                // System denies FGS starts for background-launched processes (e.g. right after
                // the app was reinstalled/killed mid-download). Retry with backoff instead of
                // failing permanently; it succeeds once the app is foregrounded again.
                Timber.w(e, "Foreground service start denied for source $sourceId, retrying")
                Result.retry()
            } catch (e: IOException) {
                Timber.e(e, "Video download failed for source $sourceId")
                if (isStopped) {
                    NotificationManagerCompat.from(applicationContext).cancel(notificationId)
                    Result.failure()
                } else {
                    notify(notificationId, failedNotification(itemName))
                    Result.retry()
                }
            }
        }

    private suspend fun downloadToFile(
        sourceUrl: String,
        destinationPath: String,
        expectedSize: Long,
        notificationId: Int,
        itemName: String,
    ) {
        val destFile = File(destinationPath)
        destFile.parentFile?.mkdirs()

        var downloadedSoFar = if (destFile.exists()) destFile.length() else 0L

        val client = OkHttpClient()
        val requestBuilder = Request.Builder().url(sourceUrl)
        if (downloadedSoFar > 0) {
            requestBuilder.header("Range", "bytes=$downloadedSoFar-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} for $sourceUrl")
            }

            val append = response.code == 206 && downloadedSoFar > 0
            if (!append) {
                downloadedSoFar = 0L
            }

            val body = response.body
            val contentLength = body.contentLength()
            val totalBytes =
                when {
                    contentLength > 0 && append -> downloadedSoFar + contentLength
                    contentLength > 0 -> contentLength
                    expectedSize > 0 -> expectedSize
                    else -> -1L
                }

            body.byteStream().use { input ->
                FileOutputStream(destFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastReportMs = System.currentTimeMillis()
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedSoFar += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportMs >= PROGRESS_INTERVAL_MS) {
                            setProgress(
                                workDataOf(KEY_DOWNLOADED to downloadedSoFar, KEY_TOTAL to totalBytes)
                            )
                            val percent =
                                if (totalBytes > 0) {
                                    (downloadedSoFar.times(100).div(totalBytes)).toInt()
                                } else {
                                    0
                                }
                            notify(
                                notificationId,
                                progressNotification(itemName, percent, totalBytes <= 0),
                            )
                            lastReportMs = now
                        }
                    }
                    output.flush()
                }
            }

            if (isStopped) throw IOException("Download cancelled")

            setProgress(workDataOf(KEY_DOWNLOADED to downloadedSoFar, KEY_TOTAL to totalBytes))

            if (totalBytes > 0 && downloadedSoFar != totalBytes) {
                throw IOException(
                    "Incomplete download: got $downloadedSoFar of $totalBytes bytes for $sourceUrl"
                )
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(CoreR.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        applicationContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun foregroundInfo(notificationId: Int, notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notify(notificationId: Int, notification: Notification) {
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun progressNotification(
        itemName: String,
        percent: Int,
        indeterminate: Boolean,
    ): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.downloading_item, itemName))
            .apply { if (!indeterminate) setContentText("$percent%") }
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, indeterminate)
            .build()
    }

    private fun completeNotification(itemName: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.download_complete_item, itemName))
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun failedNotification(itemName: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.download_failed_item, itemName))
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val KEY_SOURCE_ID = "KEY_SOURCE_ID"
        const val KEY_SOURCE_URL = "KEY_SOURCE_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_FINAL_PATH = "KEY_FINAL_PATH"
        const val KEY_EXPECTED_SIZE = "KEY_EXPECTED_SIZE"
        const val KEY_ITEM_NAME = "KEY_ITEM_NAME"
        const val KEY_DOWNLOADED = "KEY_DOWNLOADED"
        const val KEY_TOTAL = "KEY_TOTAL"

        private const val CHANNEL_ID = "downloads"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 1000L
    }
}
