package dev.jdtech.jellyfin.core.presentation.downloader

/**
 * Result of estimating what a season's bulk download would actually pull, given a scope (e.g.
 * unwatched-only): [itemCount] episodes not already downloaded locally, totalling [sizeBytes].
 */
data class DownloadSizeEstimate(val sizeBytes: Long = 0, val itemCount: Int = 0) {
    operator fun plus(other: DownloadSizeEstimate) =
        DownloadSizeEstimate(sizeBytes + other.sizeBytes, itemCount + other.itemCount)
}
