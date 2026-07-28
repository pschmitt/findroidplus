package dev.jdtech.jellyfin.localcontrol

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.work.NotificationChannels

/**
 * Posts (and later cancels) the "Pairing request from X - Approve/Deny" notification a
 * [LocalControlServer] pairing handshake waits on. [packageName] comes from the connecting
 * process's kernel-verified uid ([android.net.LocalSocket.getPeerCredentials]), not anything the
 * client declared about itself - shown here so the user knows *which app* is actually asking, not
 * just a self-reported label that could say anything.
 */
object PairingNotifier {
    fun show(context: Context, requestId: String, packageName: String) {
        NotificationChannels.ensurePairingRequests(context)
        val builder =
            NotificationCompat.Builder(context, NotificationChannels.PAIRING_REQUESTS)
                .setSmallIcon(CoreR.drawable.ic_smartphone)
                .setContentTitle(context.getString(CoreR.string.pairing_notification_title))
                .setContentText(
                    context.getString(CoreR.string.pairing_notification_text, packageName)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOngoing(false)
                .addAction(
                    0,
                    context.getString(CoreR.string.pairing_notification_approve),
                    actionPendingIntent(context, requestId, PairingActionReceiver.ACTION_APPROVE),
                )
                .addAction(
                    0,
                    context.getString(CoreR.string.pairing_notification_deny),
                    actionPendingIntent(context, requestId, PairingActionReceiver.ACTION_DENY),
                )
        NotificationManagerCompat.from(context).notify(notificationId(requestId), builder.build())
    }

    fun cancel(context: Context, requestId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(requestId))
    }

    private fun actionPendingIntent(context: Context, requestId: String, action: String): PendingIntent {
        val intent =
            Intent(context, PairingActionReceiver::class.java).apply {
                this.action = action
                putExtra(PairingActionReceiver.EXTRA_REQUEST_ID, requestId)
            }
        return PendingIntent.getBroadcast(
            context,
            "$action:$requestId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(requestId: String) = NOTIFICATION_ID_BASE + requestId.hashCode()

    private const val NOTIFICATION_ID_BASE = 279_418_000
}
