package dev.pschmitt.jellyfin.work

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.utils.DownloadProgress
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the whole download session as one continuous foreground service, rather than the previous
 * design (VideoDownloadWorker, removed) where every single WorkManager execution attempt -
 * including retries fired hours later while the app is backgrounded - independently called
 * `setForeground()`. Android refuses to promote a *new* foreground service from a background
 * process, so that design meant any retry that happened to fire while backgrounded got denied,
 * backed off, denied again, etc. - the item would sit at "Queued" for hours with nothing actually
 * transferring. See the "Real fix for stuck background downloads" plan for the full writeup.
 *
 * This service calls [startForeground] exactly once, only from moments guaranteed to be
 * foreground-eligible (see [dev.pschmitt.jellyfin.utils.DownloaderImpl] call sites and
 * [ForegroundDownloadResumer]), then stays alive and foreground for as long as
 * [DownloadQueueRepository] has work queued. A transient transfer failure becomes an internal retry
 * loop *inside this already-running instance* - never a fresh `startForegroundService()` call - so
 * `ForegroundServiceStartNotAllowedException` has nothing to trigger on in the common case (start
 * while foreground, keep running through backgrounding).
 */
@AndroidEntryPoint
class VideoDownloadService : Service() {
    @Inject lateinit var database: ServerDatabaseDao
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var downloadQueueRepository: DownloadQueueRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferEngine = VideoTransferEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureDownloads(applicationContext)
        try {
            startForeground(
                DownloadNotificationCoordinator.NOTIFICATION_ID,
                DownloadNotificationCoordinator.buildNotification(applicationContext),
                foregroundServiceType(),
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) - the app went back to the
            // background between whatever triggered this start and this call actually running.
            // Leave every pending request as-is (still in the repository, DB-backed) and surface
            // it to the UI rather than pretending we're transferring; ResumeDownloadsJobService
            // (API 34+) or the app being reopened (ForegroundDownloadResumer) will retry the start.
            Timber.w(e, "VideoDownloadService could not promote itself to foreground")
            downloadQueueRepository.markAwaitingForeground(
                downloadQueueRepository.unclaimedRequests().map { it.sourceId }
            )
            ResumeDownloadsJobService.schedule(applicationContext)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sync()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun sync() {
        for (request in downloadQueueRepository.unclaimedRequests()) {
            val job = serviceScope.launch { runTransfer(request) }
            downloadQueueRepository.trackJob(request.sourceId, job)
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    DownloadNotificationCoordinator.remove(applicationContext, request.sourceId)
                }
                maybeStopSelf()
            }
        }
        // Nothing was pending at all when this onStartCommand fired (e.g. a stray restart) -
        // the loop above never ran, so nothing will call maybeStopSelf() on our behalf.
        if (!downloadQueueRepository.hasWork()) {
            maybeStopSelf()
        }
    }

    private fun maybeStopSelf() {
        if (downloadQueueRepository.hasWork()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runTransfer(request: VideoDownloadRequest) {
        val (downloadId, sourceId, sourceUrl, destinationPath, finalPath, expectedSize, itemName) =
            request
        val completionNotificationId = sourceId.hashCode()

        try {
            reportQueued(sourceId, downloadId, itemName)
            val maxParallel = appPreferences.getValue(appPreferences.maxParallelDownloads)
            DownloadSlotLimiter.acquire(sourceId, maxParallel)
            try {
                downloadQueueRepository.updateProgress(
                    sourceId,
                    DownloadProgress(status = android.app.DownloadManager.STATUS_RUNNING),
                )
                reportProgress(sourceId, downloadId, itemName, 0, 0L, 0L, 0L)
                downloadToFile(
                    sourceUrl,
                    destinationPath,
                    expectedSize,
                    sourceId,
                    downloadId,
                    itemName,
                )
            } finally {
                withContext(NonCancellable) { DownloadSlotLimiter.release() }
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
            downloadQueueRepository.markSucceeded(sourceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Video download failed for source $sourceId")
            DownloadNotificationCoordinator.remove(applicationContext, sourceId)
            notify(completionNotificationId, failedNotification(itemName))
            downloadQueueRepository.markFailed(sourceId)
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
        transferEngine.download(sourceUrl, destinationPath, expectedSize, PROGRESS_INTERVAL_MS) {
            downloadedBytes,
            totalBytes,
            percent,
            speedBytesPerSecond ->
            val etaSeconds =
                if (speedBytesPerSecond > 0 && totalBytes > 0) {
                    (totalBytes - downloadedBytes) / speedBytesPerSecond
                } else {
                    -1L
                }
            downloadQueueRepository.updateProgress(
                sourceId,
                DownloadProgress(
                    status = android.app.DownloadManager.STATUS_RUNNING,
                    percent = percent,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = speedBytesPerSecond,
                    etaSeconds = etaSeconds,
                ),
            )
            reportProgress(
                sourceId,
                downloadId,
                itemName,
                percent,
                downloadedBytes,
                totalBytes,
                speedBytesPerSecond,
            )
        }
    }

    /**
     * Re-reads the just-downloaded file end to end to compute its SHA-256 checksum, reported as a
     * distinct "Verifying" phase (notification + repository progress) rather than folded silently
     * into the download itself.
     */
    private suspend fun verifyAndHash(
        file: File,
        sourceId: String,
        downloadId: Long,
        itemName: String,
    ): String {
        reportVerifying(sourceId, downloadId, itemName, 0)
        downloadQueueRepository.updateProgress(
            sourceId,
            DownloadProgress(
                status = DownloadProgress.STATUS_VERIFYING,
                downloadedBytes = 0L,
                totalBytes = file.length(),
            ),
        )
        return transferEngine.verifyAndHash(file, PROGRESS_INTERVAL_MS) {
            hashedBytes,
            totalBytes,
            percent ->
            downloadQueueRepository.updateProgress(
                sourceId,
                DownloadProgress(
                    status = DownloadProgress.STATUS_VERIFYING,
                    percent = percent,
                    downloadedBytes = hashedBytes,
                    totalBytes = totalBytes,
                ),
            )
            reportVerifying(sourceId, downloadId, itemName, percent)
        }
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

    private fun reportVerifying(
        sourceId: String,
        downloadId: Long,
        itemName: String,
        percent: Int,
    ) {
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

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
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
        private const val CHANNEL_ID = NotificationChannels.DOWNLOADS
        private const val PROGRESS_INTERVAL_MS = 1000L
    }
}
