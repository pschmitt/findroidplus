package dev.jdtech.jellyfin.models

/**
 * A single Jellyfin item's current Sonarr/Radarr queue entry, already resolved against a real
 * Jellyfin item id by [dev.jdtech.jellyfin.repository.QueueStatusRepository] - never surfaced for
 * an item Findroid couldn't match (see `matchSonarr`/`matchRadarr` in `QueueStatusMatching.kt`).
 */
data class QueueStatus(
    val source: PvrSource,
    val status: QueueItemStatus,
    val percent: Int = -1,
    val sizeBytes: Long = 0L,
    val remainingBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val errorMessage: String? = null,
)

enum class PvrSource { SONARR, RADARR }

enum class QueueItemStatus { QUEUED, DOWNLOADING, IMPORTING, WARNING, FAILED }
