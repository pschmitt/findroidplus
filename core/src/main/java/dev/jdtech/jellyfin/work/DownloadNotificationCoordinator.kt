package dev.jdtech.jellyfin.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.utils.formatDownloadSpeed
import dev.jdtech.jellyfin.utils.formatEta
import java.util.concurrent.ConcurrentHashMap

/**
 * Every [VideoDownloadWorker] reports into this instead of posting its own notification, so
 * downloading a whole season doesn't flood the shade with one ongoing entry per episode - they
 * all collapse into a single shared notification (NOTIFICATION_ID). With exactly one active
 * download it still shows that item's own name/percent/speed/ETA and Pause/Cancel actions,
 * unchanged from before; with several at once it switches to an aggregate summary and drops the
 * per-item actions, since it's ambiguous which download a shared button would target - individual
 * control still lives on the Downloads page.
 */
internal object DownloadNotificationCoordinator {
    const val NOTIFICATION_ID = 279_412_001

    data class Entry(
        val itemName: String,
        val downloadId: Long,
        val queued: Boolean,
        val percent: Int = -1,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBytesPerSecond: Long = 0L,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun update(context: Context, sourceId: String, entry: Entry) {
        entries[sourceId] = entry
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context))
    }

    fun remove(context: Context, sourceId: String) {
        entries.remove(sourceId)
        if (entries.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } else {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context))
        }
    }

    fun buildNotification(context: Context): Notification {
        val snapshot = entries.values.toList()
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(downloadsContentIntent(context))

        when (snapshot.size) {
            0 ->
                builder
                    .setContentTitle(context.getString(CoreR.string.download_downloading))
                    .setProgress(0, 0, true)
            1 -> applySingle(builder, context, snapshot.first())
            else -> applyAggregate(builder, context, snapshot)
        }

        return builder.build()
    }

    private fun applySingle(builder: NotificationCompat.Builder, context: Context, entry: Entry) {
        builder.setContentTitle(context.getString(CoreR.string.downloading_item, entry.itemName))
        if (entry.queued) {
            builder
                .setContentText(context.getString(CoreR.string.download_queued))
                .setProgress(0, 0, true)
                .addAction(
                    CoreR.drawable.ic_x,
                    context.getString(CoreR.string.download_action_cancel),
                    actionPendingIntent(context, DownloadActionReceiver.ACTION_CANCEL, entry.downloadId),
                )
            return
        }

        val indeterminate = entry.totalBytes <= 0
        val etaSeconds =
            if (entry.speedBytesPerSecond > 0) {
                (entry.totalBytes - entry.downloadedBytes).coerceAtLeast(0) / entry.speedBytesPerSecond
            } else {
                -1L
            }
        val statusText =
            when {
                !indeterminate && entry.speedBytesPerSecond > 0 ->
                    context.getString(
                        CoreR.string.download_progress_status,
                        entry.percent,
                        formatDownloadSpeed(context, entry.speedBytesPerSecond),
                        formatEta(etaSeconds),
                    )
                !indeterminate -> "${entry.percent}%"
                entry.speedBytesPerSecond > 0 ->
                    context.getString(
                        CoreR.string.download_progress_speed_only,
                        formatDownloadSpeed(context, entry.speedBytesPerSecond),
                    )
                else -> null
            }
        builder
            .apply { statusText?.let { setContentText(it) } }
            .setProgress(100, entry.percent.coerceAtLeast(0), indeterminate)
            .addAction(
                CoreR.drawable.ic_pause,
                context.getString(CoreR.string.download_action_pause),
                actionPendingIntent(context, DownloadActionReceiver.ACTION_PAUSE, entry.downloadId),
            )
            .addAction(
                CoreR.drawable.ic_x,
                context.getString(CoreR.string.download_action_cancel),
                actionPendingIntent(context, DownloadActionReceiver.ACTION_CANCEL, entry.downloadId),
            )
    }

    private fun applyAggregate(builder: NotificationCompat.Builder, context: Context, snapshot: List<Entry>) {
        val running = snapshot.filter { !it.queued && it.totalBytes > 0 }
        val downloadedBytes = running.sumOf { it.downloadedBytes }
        val totalBytes = running.sumOf { it.totalBytes }
        val speedBytesPerSecond = running.sumOf { it.speedBytesPerSecond }
        val percent = if (totalBytes > 0) (downloadedBytes.times(100) / totalBytes).toInt() else -1
        val etaSeconds =
            if (speedBytesPerSecond > 0 && totalBytes > 0) {
                (totalBytes - downloadedBytes).coerceAtLeast(0) / speedBytesPerSecond
            } else {
                -1L
            }

        val progressText =
            if (percent >= 0) {
                context.getString(
                    CoreR.string.download_progress_status,
                    percent,
                    formatDownloadSpeed(context, speedBytesPerSecond),
                    formatEta(etaSeconds),
                )
            } else {
                context.getString(CoreR.string.download_queued)
            }
        val runningNames = running.joinToString(", ") { it.itemName }
        val collapsedText = if (runningNames.isNotEmpty()) "$runningNames • $progressText" else progressText

        builder
            .setContentTitle(
                context.getString(
                    CoreR.string.downloading_items_count,
                    running.size,
                    snapshot.size,
                )
            )
            .setContentText(collapsedText)
            .setStyle(inboxStyle(context, snapshot, progressText))
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(
                CoreR.drawable.ic_pause,
                context.getString(CoreR.string.download_action_pause),
                actionPendingIntent(context, DownloadActionReceiver.ACTION_PAUSE_ALL, downloadId = -1L),
            )
    }

    /** downloadIds of every currently tracked (queued or running) download. */
    fun activeDownloadIds(): List<Long> = entries.values.map { it.downloadId }

    /** downloadIds of downloads currently transferring bytes, i.e. holding a slot. */
    fun runningDownloadIds(): List<Long> = entries.values.filter { !it.queued }.map { it.downloadId }

    private fun inboxStyle(
        context: Context,
        snapshot: List<Entry>,
        summaryText: String,
    ): NotificationCompat.InboxStyle {
        val style = NotificationCompat.InboxStyle()
        style.setSummaryText(summaryText)
        // Running entries first so the items currently being transferred are visible without
        // scrolling past whatever's still queued behind the parallel-download slot limiter.
        snapshot
            .sortedBy { it.queued }
            .forEach { entry ->
                val status =
                    if (entry.queued) {
                        context.getString(CoreR.string.download_queued)
                    } else {
                        "${entry.percent.coerceAtLeast(0)}%"
                    }
                style.addLine("${entry.itemName} · $status")
            }
        return style
    }

    private fun actionPendingIntent(context: Context, action: String, downloadId: Long): PendingIntent {
        val intent =
            Intent(context, DownloadActionReceiver::class.java).apply {
                this.action = action
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            }
        return PendingIntent.getBroadcast(
            context,
            "$action:$downloadId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val CHANNEL_ID = "downloads"
}
