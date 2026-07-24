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
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Streams the primary media source to disk in fixed-size chunks under our own control, instead of
 * delegating to the system DownloadManager. DownloadManager is the actual root cause of the
 * >4GiB failure - see DownloaderImpl for the full writeup. Byte counts here are Long end-to-end.
 *
 * The ongoing progress notification is shared across every concurrently running/queued download
 * via [DownloadNotificationCoordinator] rather than posted per-item, so downloading a whole season
 * doesn't flood the shade with one entry per episode.
 */
@HiltWorker
class VideoDownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
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
            val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
            val completionNotificationId = sourceId.hashCode()

            NotificationChannels.ensureDownloads(applicationContext)

            try {
                reportQueued(sourceId, downloadId, itemName)
                setForeground(
                    foregroundInfo(
                        DownloadNotificationCoordinator.NOTIFICATION_ID,
                        DownloadNotificationCoordinator.buildNotification(applicationContext),
                    )
                )

                val maxParallel = appPreferences.getValue(appPreferences.maxParallelDownloads)
                // Mark this worker as queued in WorkManager's own progress Data too - it's already
                // RUNNING in WorkManager's eyes once setForeground() is called, even though we're
                // still blocked here waiting for a parallel-download slot, so the Downloads screen
                // (which reads workInfo.progress, unlike the notification coordinator) would
                // otherwise show "Downloading..." for an item that hasn't started transferring yet.
                setProgress(workDataOf(KEY_QUEUED to true))
                DownloadSlotLimiter.acquire(sourceId, maxParallel)
                try {
                    setProgress(workDataOf())
                    reportProgress(sourceId, downloadId, itemName, 0, 0L, 0L, 0L)
                    downloadToFile(sourceUrl, destinationPath, expectedSize, sourceId, downloadId, itemName)
                } finally {
                    DownloadSlotLimiter.release()
                }

                val checksum = verifyAndHash(File(destinationPath), sourceId, downloadId, itemName)
                database.setSourceChecksum(sourceId, checksum)

                val destFile = File(destinationPath)
                val finalFile = File(finalPath)
                if (!destFile.renameTo(finalFile)) {
                    throw IOException("Failed to rename $destinationPath to $finalPath")
                }
                database.setSourcePath(sourceId, finalPath)

                DownloadNotificationCoordinator.remove(applicationContext, sourceId)
                notify(completionNotificationId, completeNotification(itemName))
                Result.success()
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                // System denies FGS starts for background-launched processes (e.g. right after
                // the app was reinstalled/killed mid-download). Retry with backoff instead of
                // failing permanently; it succeeds once the app is foregrounded again.
                Timber.w(e, "Foreground service start denied for source $sourceId, retrying")
                Result.retry()
            } catch (e: IOException) {
                Timber.e(e, "Video download failed for source $sourceId")
                DownloadNotificationCoordinator.remove(applicationContext, sourceId)
                if (isStopped) {
                    Result.failure()
                } else {
                    notify(completionNotificationId, failedNotification(itemName))
                    Result.retry()
                }
            }
        }

    private suspend fun downloadToFile(
        sourceUrl: String,
        destinationPath: String,
        expectedSize: Long,
        sourceId: String,
        downloadId: Long,
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
                    var bytesAtLastReport = downloadedSoFar
                    while (!isStopped) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedSoFar += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastReportMs
                        if (elapsedMs >= PROGRESS_INTERVAL_MS) {
                            setProgress(
                                workDataOf(KEY_DOWNLOADED to downloadedSoFar, KEY_TOTAL to totalBytes)
                            )
                            val percent =
                                if (totalBytes > 0) {
                                    (downloadedSoFar.times(100).div(totalBytes)).toInt()
                                } else {
                                    0
                                }
                            val bytesSinceLast = downloadedSoFar - bytesAtLastReport
                            val speedBytesPerSecond =
                                bytesSinceLast.times(1000).div(elapsedMs.coerceAtLeast(1))
                            reportProgress(
                                sourceId,
                                downloadId,
                                itemName,
                                percent,
                                downloadedSoFar,
                                totalBytes,
                                speedBytesPerSecond,
                            )
                            lastReportMs = now
                            bytesAtLastReport = downloadedSoFar
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

    /**
     * Re-reads the just-downloaded file end to end to compute its SHA-256 checksum, reported as a
     * distinct "Verifying" phase (notification + WorkManager progress) rather than folded silently
     * into the download itself. A separate pass (rather than hashing while writing) is deliberate:
     * it reads the complete file regardless of how many pause/resume cycles built it up, so the
     * checksum is always over the final bytes, not whatever happened to be in a hasher's memory
     * across worker restarts.
     */
    private suspend fun verifyAndHash(
        file: File,
        sourceId: String,
        downloadId: Long,
        itemName: String,
    ): String {
        val totalBytes = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        var hashedSoFar = 0L
        var lastReportMs = System.currentTimeMillis()

        reportVerifying(sourceId, downloadId, itemName, 0)
        setProgress(workDataOf(KEY_VERIFYING to true, KEY_DOWNLOADED to 0L, KEY_TOTAL to totalBytes))

        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (!isStopped) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
                hashedSoFar += read

                val now = System.currentTimeMillis()
                if (now - lastReportMs >= PROGRESS_INTERVAL_MS) {
                    val percent =
                        if (totalBytes > 0) (hashedSoFar.times(100).div(totalBytes)).toInt() else 0
                    setProgress(
                        workDataOf(KEY_VERIFYING to true, KEY_DOWNLOADED to hashedSoFar, KEY_TOTAL to totalBytes)
                    )
                    reportVerifying(sourceId, downloadId, itemName, percent)
                    lastReportMs = now
                }
            }
        }

        if (isStopped) throw IOException("Verification cancelled")

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun reportQueued(sourceId: String, downloadId: Long, itemName: String) {
        DownloadNotificationCoordinator.update(
            applicationContext,
            sourceId,
            DownloadNotificationCoordinator.Entry(
                itemName = itemName,
                downloadId = downloadId,
                queued = true,
            ),
        )
    }

    private fun reportVerifying(sourceId: String, downloadId: Long, itemName: String, percent: Int) {
        DownloadNotificationCoordinator.update(
            applicationContext,
            sourceId,
            DownloadNotificationCoordinator.Entry(
                itemName = itemName,
                downloadId = downloadId,
                queued = false,
                verifying = true,
                percent = percent,
            ),
        )
    }

    private fun reportProgress(
        sourceId: String,
        downloadId: Long,
        itemName: String,
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
    ) {
        DownloadNotificationCoordinator.update(
            applicationContext,
            sourceId,
            DownloadNotificationCoordinator.Entry(
                itemName = itemName,
                downloadId = downloadId,
                queued = false,
                percent = percent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBytesPerSecond = speedBytesPerSecond,
            ),
        )
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

    private fun completeNotification(itemName: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.download_complete))
            .setContentText(itemName)
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsContentIntent(applicationContext))
            .build()
    }

    private fun failedNotification(itemName: String): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(CoreR.string.download_failed))
            .setContentText(itemName)
            .setSmallIcon(CoreR.drawable.ic_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(downloadsContentIntent(applicationContext))
            .build()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "KEY_DOWNLOAD_ID"
        const val KEY_SOURCE_ID = "KEY_SOURCE_ID"
        const val KEY_SOURCE_URL = "KEY_SOURCE_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_FINAL_PATH = "KEY_FINAL_PATH"
        const val KEY_EXPECTED_SIZE = "KEY_EXPECTED_SIZE"
        const val KEY_ITEM_NAME = "KEY_ITEM_NAME"
        const val KEY_DOWNLOADED = "KEY_DOWNLOADED"
        const val KEY_TOTAL = "KEY_TOTAL"
        const val KEY_QUEUED = "KEY_QUEUED"
        const val KEY_VERIFYING = "KEY_VERIFYING"

        private const val CHANNEL_ID = NotificationChannels.DOWNLOADS
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 1000L
    }
}
