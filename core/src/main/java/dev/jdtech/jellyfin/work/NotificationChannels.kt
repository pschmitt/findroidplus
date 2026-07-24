package dev.jdtech.jellyfin.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * Every notification channel the app posts to, defined exactly once. Each producer
 * (worker/notifier) previously declared its own private `createNotificationChannel()` with the
 * id/name/importance copy-pasted at every call site - channel registration is idempotent so that
 * was never a functional bug, but it meant a change (e.g. adding a description) had to be made in
 * every copy or they'd drift.
 */
object NotificationChannels {
    const val DOWNLOADS = "downloads"
    const val PVR_DOWNLOADS = "pvr_downloads"
    const val NEW_ITEMS = "new_items"
    const val PENDING_DOWNLOADS = "pending_downloads"
    const val AUTOMATIC_SEARCH = "automatic_search"

    /** Download/delete/move progress and completion for locally downloaded items. */
    fun ensureDownloads(context: Context) =
        ensure(
            context,
            DOWNLOADS,
            CoreR.string.download_notification_channel_name,
            CoreR.string.download_notification_channel_description,
            NotificationManager.IMPORTANCE_LOW,
        )

    /** Sonarr/Radarr queue entries that finished importing into the library. */
    fun ensurePvrDownloads(context: Context) =
        ensure(
            context,
            PVR_DOWNLOADS,
            CoreR.string.pvr_download_channel_name,
            CoreR.string.pvr_download_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )

    /** Newly added movies/episodes detected in the Jellyfin library. */
    fun ensureNewItems(context: Context) =
        ensure(
            context,
            NEW_ITEMS,
            CoreR.string.new_items_channel_name,
            CoreR.string.new_items_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )

    /** Pre-ordered "download when available" requests that were fulfilled. */
    fun ensurePendingDownloads(context: Context) =
        ensure(
            context,
            PENDING_DOWNLOADS,
            CoreR.string.pending_download_channel_name,
            CoreR.string.pending_download_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )

    /** Result of a manually-triggered Sonarr/Radarr automatic search. */
    fun ensureAutomaticSearch(context: Context) =
        ensure(
            context,
            AUTOMATIC_SEARCH,
            CoreR.string.automatic_search_channel_name,
            CoreR.string.automatic_search_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )

    private fun ensure(
        context: Context,
        id: String,
        @StringRes nameRes: Int,
        @StringRes descriptionRes: Int,
        importance: Int,
    ) {
        val channel = NotificationChannel(id, context.getString(nameRes), importance)
        channel.description = context.getString(descriptionRes)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
