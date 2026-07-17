package dev.jdtech.jellyfin.film.presentation.library

import androidx.paging.PagingData
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class LibraryState(
    val items: Flow<PagingData<FindroidItem>> = emptyFlow(),
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val isLoading: Boolean = false,
    val error: Exception? = null,
    val searchQuery: String = "",
    val queueStatus: Map<UUID, QueueStatus> = emptyMap(),
    // Only used by the merged "Media" view (no parent library), where movies and shows are
    // browsed together and can be narrowed down.
    val filter: MediaFilter = MediaFilter.ALL,
    // Seerr integration, only active in the merged Media view: searching the library also
    // searches Seerr so content that isn't on disk can be requested in place.
    val seerrConfigured: Boolean = false,
    val seerrResults: List<SeerrSearchItem> = emptyList(),
    val seerrSearching: Boolean = false,
    // Already user-presentable (names Seerr).
    val seerrError: String? = null,
    val recentRequests: List<SeerrRequestItem> = emptyList(),
    // TMDB ids requested during this screen's lifetime - overlays the (now stale) status
    // carried by the search results, so a just-requested item immediately reads as "Requested".
    val requestedTmdbIds: Set<Int> = emptySet(),
)

/** One-shot feedback for a Seerr request action, shown as a toast. */
sealed interface LibraryEvent {
    data class SeerrRequested(val title: String) : LibraryEvent

    data class SeerrRequestFailed(val message: String?) : LibraryEvent
}

/** Item-kind filter for the merged movies+shows "Media" view. */
enum class MediaFilter {
    ALL,
    MOVIES,
    SHOWS,
}
