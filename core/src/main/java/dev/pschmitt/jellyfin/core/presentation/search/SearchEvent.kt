package dev.pschmitt.jellyfin.core.presentation.search

/**
 * One-shot feedback for a Sonarr search/grab action, shown as a toast. Same channel-based one-shot
 * pattern as `DownloaderEvent`.
 */
sealed interface SearchEvent {
    data object SearchTriggered : SearchEvent

    data object ReleaseGrabbed : SearchEvent

    data class Failed(val message: String?) : SearchEvent
}
