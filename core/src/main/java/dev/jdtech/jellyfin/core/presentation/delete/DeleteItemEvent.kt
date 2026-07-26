package dev.jdtech.jellyfin.core.presentation.delete

/**
 * One-shot feedback for a "delete from Jellyfin" action, shared by Movie/Show/Episode's
 * ViewModels - same channel-based one-shot pattern as `SearchEvent`/`DownloaderEvent`. [Deleted]
 * tells the screen to navigate back (the item no longer exists); [Failed] is shown as a toast.
 */
sealed interface DeleteItemEvent {
    data object Deleted : DeleteItemEvent

    data class Failed(val message: String?) : DeleteItemEvent
}
