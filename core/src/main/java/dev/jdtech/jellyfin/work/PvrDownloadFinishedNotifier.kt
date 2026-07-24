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
 * Posts a notification when a Sonarr/Radarr queue entry finishes importing (detected by
 * `QueueStatusRepositoryImpl` diffing queue polls - see `notifyFinishedDownloads` there). This is
 * the counterpart of [AutomaticSearchWorker]'s "found/not found" notification, covering the other
 * end of the pipeline: the download actually landing in the library.
 */
@Singleton
class PvrDownloadFinishedNotifier
@Inject
constructor(@ApplicationContext private val context: Context) {

    fun notifyFinished(title: String) {
        NotificationChannels.ensurePvrDownloads(context)

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(context.getString(CoreR.string.pvr_download_finished_notification))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent())
                .build()

        // The title identifies the download well enough - concurrent finishes for different items
        // get their own notifications, a re-download of the same item replaces the previous one.
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
        const val CHANNEL_ID = NotificationChannels.PVR_DOWNLOADS
        private const val NOTIFICATION_ID_BASE = 279_414_000
    }
}
