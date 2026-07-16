package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.utils.DeleteProgress
import dev.jdtech.jellyfin.utils.DownloadProgress
import java.util.UUID

data class DownloadsState(
    val isLoading: Boolean = false,
    val error: Exception? = null,
    val movies: List<FindroidMovie> = emptyList(),
    val showGroups: List<DownloadShowGroup> = emptyList(),
    val selectedIds: Set<UUID> = emptySet(),
    val downloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val deleteProgress: DeleteProgress? = null,
    val pvrQueueGroups: List<PvrQueueGroup> = emptyList(),
) {
    val isEmpty: Boolean
        get() = movies.isEmpty() && showGroups.isEmpty()
}

data class DownloadShowGroup(
    val seriesId: UUID,
    val seriesName: String,
    val episodes: List<FindroidEpisode>,
)

/**
 * A single Sonarr/Radarr queue entry as shown on the Downloads screen. [itemId]/[item] are only
 * non-null when [QueueStatusRepository][dev.jdtech.jellyfin.repository.QueueStatusRepository]
 * resolved this queue entry to an item already present in the local Jellyfin library - otherwise
 * this is a title-only row (no poster, not clickable).
 */
data class PvrQueueUiItem(
    val itemId: UUID?,
    val title: String,
    val item: FindroidItem? = null,
    val status: QueueStatus,
)

data class PvrQueueGroup(val source: PvrSource, val items: List<PvrQueueUiItem>)
