package dev.pschmitt.jellyfin.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * Extras on a new-item notification's content [PendingIntent] so the host app can navigate straight
 * to the item it's about when tapped, mirroring [EXTRA_OPEN_DOWNLOADS] / [downloadsContentIntent]
 * for the Downloads screen.
 */
const val EXTRA_OPEN_ITEM_ID = "dev.pschmitt.jellyfin.EXTRA_OPEN_ITEM_ID"
const val EXTRA_OPEN_ITEM_IS_MOVIE = "dev.pschmitt.jellyfin.EXTRA_OPEN_ITEM_IS_MOVIE"

// Built via getLaunchIntentForPackage() instead of referencing MainActivity directly - :core
// doesn't (and shouldn't) depend on the :app modules that host it.
internal fun openItemPendingIntent(
    context: Context,
    itemId: UUID,
    isMovie: Boolean,
    requestCode: Int,
): PendingIntent? {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ITEM_ID, itemId.toString())
            putExtra(EXTRA_OPEN_ITEM_IS_MOVIE, isMovie)
        } ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

// Same idea, but with no specific item to deep-link to (large batches - see NewItemNotifier).
internal fun openAppPendingIntent(context: Context): PendingIntent? {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: return null
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
