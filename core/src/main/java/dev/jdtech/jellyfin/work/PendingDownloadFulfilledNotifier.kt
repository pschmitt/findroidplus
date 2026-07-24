package dev.jdtech.jellyfin.work

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R as CoreR
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a notification when a pre-ordered "download when available" request (see
 * `dev.jdtech.jellyfin.models.PendingDownloadRequestDto`) is fulfilled by
 * [PendingDownloadWorker] (via `PendingDownloadFulfiller`) - the season/episode the user queued
 * while it only existed in Sonarr has now appeared in the Jellyfin library and started
 * downloading. Same pattern as [PvrDownloadFinishedNotifier] (lazy channel creation, tap opens the
 * app), kept on a separate channel so users can mute one without the other.
 */
@Singleton
class PendingDownloadFulfilledNotifier
@Inject
constructor(@ApplicationContext private val context: Context) {

    fun notifyFulfilled(title: String) {
        NotificationChannels.ensurePendingDownloads(context)

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(context.getString(CoreR.string.pending_download_fulfilled_notification))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent())
                .build()

        // The title identifies the download well enough - concurrent fulfillments for different
        // items get their own notifications, a re-fulfillment of the same title replaces the
        // previous one.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + title.hashCode(), notification)
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = NotificationChannels.PENDING_DOWNLOADS
        private const val NOTIFICATION_ID_BASE = 279_415_000
    }
}
