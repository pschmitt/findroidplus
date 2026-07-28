package dev.pschmitt.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import dev.pschmitt.jellyfin.models.UiText
import dev.pschmitt.jellyfin.utils.DownloadProgress

data class DownloaderState(
    val status: Int = 0,
    val progress: Float = 0f,
    val errorText: UiText? = null,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isDeleting: Boolean = false,
) {
    val isDownloading: Boolean
        get() =
            status in
                arrayOf(
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_FAILED,
                    DownloadProgress.STATUS_VERIFYING,
                    DownloadProgress.STATUS_AWAITING_FOREGROUND,
                )
}
