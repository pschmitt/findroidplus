package dev.jdtech.jellyfin.film.presentation.downloads

sealed interface DownloadAction {
    data object Pause : DownloadAction

    data object Resume : DownloadAction

    data object Cancel : DownloadAction

    data object Force : DownloadAction
}
