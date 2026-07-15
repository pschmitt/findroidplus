package dev.jdtech.jellyfin.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Extra on the notification's content [PendingIntent] so the host app can navigate straight to
 * the Downloads screen when the user taps a download notification.
 */
const val EXTRA_OPEN_DOWNLOADS = "dev.jdtech.jellyfin.EXTRA_OPEN_DOWNLOADS"

// Built via getLaunchIntentForPackage() instead of referencing MainActivity directly - :core
// doesn't (and shouldn't) depend on the :app modules that host it.
internal fun downloadsContentIntent(context: Context): PendingIntent? {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
        } ?: return null
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
