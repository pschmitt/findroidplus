package dev.pschmitt.jellyfin.utils

/** A snapshot of an in-flight [Downloader.deleteItems] batch, emitted by [Downloader.getDeleteProgressFlow]. */
data class DeleteProgress(val done: Int, val total: Int)
