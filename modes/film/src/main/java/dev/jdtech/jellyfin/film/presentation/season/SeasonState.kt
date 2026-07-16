package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope
import dev.jdtech.jellyfin.utils.DownloadProgress
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    val autoDownloadEnabled: Boolean = false,
    val existingScope: ExistingAutoDownloadScope = ExistingAutoDownloadScope(),
    val hasDownloads: Boolean = false,
    val downloadsSizeBytes: Long = 0L,
    val downloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val queueStatus: Map<UUID, QueueStatus> = emptyMap(),
    val error: Exception? = null,
)
