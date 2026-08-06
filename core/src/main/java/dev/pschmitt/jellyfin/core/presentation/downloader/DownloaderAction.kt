package dev.pschmitt.jellyfin.core.presentation.downloader

import dev.pschmitt.jellyfin.models.JollyfinItem

sealed interface DownloaderAction {
    data class Download(val item: JollyfinItem, val storageIndex: Int = 0) : DownloaderAction

    /**
     * Immediate single-item download push (FINDROID-44) - the "this episode" remote-device case,
     * which has no season/rule scope at all so it doesn't go through ShowAction/SeasonAction/
     * EpisodeAction.DownloadWithScope's targetDeviceId branch.
     */
    data class PushDownload(val item: JollyfinItem, val targetDeviceId: String) : DownloaderAction

    data class DeleteDownload(val item: JollyfinItem) : DownloaderAction

    data class CancelDownload(val item: JollyfinItem) : DownloaderAction

    data object ForceDownload : DownloaderAction

    data object PauseDownload : DownloaderAction

    data object ResumeDownload : DownloaderAction
}
