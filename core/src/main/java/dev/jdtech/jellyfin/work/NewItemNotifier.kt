package dev.jdtech.jellyfin.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R as CoreR
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a batched notification when [NewItemNotificationWorker] finds movies/episodes newly
 * added to the Jellyfin library since the last check. Multiple items from one check cycle
 * collapse into a single group in the notification shade instead of flooding it with one entry
 * per item, per the "grouped/batched, not one-per-item" requirement - a real Android notification
 * group (one summary + per-item child notifications sharing a `setGroup` key) rather than an
 * in-place-updated single notification like [DownloadNotificationCoordinator], since each item
 * here still needs its own independent tap target and (conditionally) its own "Download" action.
 * Beyond [MAX_CHILDREN] items in one cycle, per-item actions stop being useful in a shade that's
 * about to be dominated by one group anyway, so it falls back to a single summary-only
 * notification (an inbox-style list of titles, tap to open the app) instead of posting dozens of
 * children.
 */
@Singleton
class NewItemNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    data class NewItem(
        val id: UUID,
        val title: String,
        val isMovie: Boolean,
        // False when an enabled auto-download rule already covers this episode (or, for a movie,
        // when it isn't downloadable/already has a source) - see
        // NewItemNotificationWorker.toNotifyItem(). A redundant "Download" button next to
        // something that's about to download itself would just be confusing.
        val downloadEligible: Boolean,
    )

    fun notifyNewItems(items: List<NewItem>) {
        if (items.isEmpty()) return
        NotificationChannels.ensureNewItems(context)

        when {
            items.size == 1 -> postSingle(items.first())
            items.size <= MAX_CHILDREN -> postGroup(items)
            else -> postOverflowSummary(items)
        }
    }

    private fun postSingle(item: NewItem) {
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_bell)
                .setContentTitle(item.title)
                .setContentText(context.getString(CoreR.string.new_item_notification_text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(
                    openItemPendingIntent(
                        context,
                        item.id,
                        item.isMovie,
                        requestCode = item.id.hashCode(),
                    )
                )
        applyDownloadAction(builder, item, notificationId(item))
        NotificationManagerCompat.from(context).notify(notificationId(item), builder.build())
    }

    private fun postGroup(items: List<NewItem>) {
        // Built imperatively rather than via Kotlin's `.apply {}` - NotificationCompat.Style
        // declares its own member `apply(Notification.Builder)`, which shadows the `apply`
        // scope function and silently breaks the lambda's implicit receiver.
        val summaryStyle = NotificationCompat.InboxStyle()
        summaryStyle.setSummaryText(
            context.getString(CoreR.string.new_items_notification_title, items.size)
        )
        items.forEach { summaryStyle.addLine(it.title) }

        val summary =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_bell)
                .setContentTitle(
                    context.getString(CoreR.string.new_items_notification_title, items.size)
                )
                .setContentText(items.joinToString(", ") { it.title })
                .setStyle(summaryStyle)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent(context))
                .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary)

        items.forEach { item ->
            val builder =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(CoreR.drawable.ic_bell)
                    .setContentTitle(item.title)
                    .setContentText(context.getString(CoreR.string.new_item_notification_text))
                    .setGroup(GROUP_KEY)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(
                        openItemPendingIntent(
                            context,
                            item.id,
                            item.isMovie,
                            requestCode = item.id.hashCode(),
                        )
                    )
            applyDownloadAction(builder, item, notificationId(item))
            NotificationManagerCompat.from(context).notify(notificationId(item), builder.build())
        }
    }

    private fun postOverflowSummary(items: List<NewItem>) {
        val style = NotificationCompat.InboxStyle()
        style.setSummaryText(context.getString(CoreR.string.new_items_notification_title, items.size))
        items.take(MAX_CHILDREN).forEach { style.addLine(it.title) }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_bell)
                .setContentTitle(
                    context.getString(CoreR.string.new_items_notification_title, items.size)
                )
                .setContentText(items.joinToString(", ") { it.title })
                .setStyle(style)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent(context))
                .build()
        // Fixed id, same as the group summary - a large batch replaces whatever grouped/overflow
        // notification from a previous cycle is still sitting in the shade rather than stacking.
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun applyDownloadAction(
        builder: NotificationCompat.Builder,
        item: NewItem,
        notificationId: Int,
    ) {
        if (!item.downloadEligible) return
        builder.addAction(
            CoreR.drawable.ic_download,
            context.getString(CoreR.string.download),
            downloadActionPendingIntent(item, notificationId),
        )
    }

    private fun notificationId(item: NewItem) = NOTIFICATION_ID_BASE + item.id.hashCode()

    private fun downloadActionPendingIntent(item: NewItem, notificationId: Int): PendingIntent {
        val intent =
            Intent(context, NewItemDownloadActionReceiver::class.java).apply {
                action = NewItemDownloadActionReceiver.ACTION_DOWNLOAD
                putExtra(NewItemDownloadActionReceiver.EXTRA_ITEM_ID, item.id.toString())
                putExtra(NewItemDownloadActionReceiver.EXTRA_IS_MOVIE, item.isMovie)
                putExtra(NewItemDownloadActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
        return PendingIntent.getBroadcast(
            context,
            "download:${item.id}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = NotificationChannels.NEW_ITEMS
        const val GROUP_KEY = "dev.jdtech.jellyfin.NEW_ITEMS_GROUP"

        // Above this many new items in one cycle, per-item child notifications stop being a
        // usable list in the shade - fall back to one summary-only notification instead.
        const val MAX_CHILDREN = 6
        const val NOTIFICATION_ID_BASE = 279_417_000
        const val SUMMARY_NOTIFICATION_ID = 279_416_999
    }
}
