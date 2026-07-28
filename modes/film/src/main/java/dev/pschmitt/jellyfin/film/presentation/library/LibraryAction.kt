package dev.pschmitt.jellyfin.film.presentation.library

import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.SeerrRequestItem
import dev.pschmitt.jellyfin.models.SeerrSearchItem
import dev.pschmitt.jellyfin.models.SortBy
import dev.pschmitt.jellyfin.models.SortOrder

sealed interface LibraryAction {
    data class OnItemClick(val item: FindroidItem) : LibraryAction

    data object OnBackClick : LibraryAction

    data class ChangeSorting(val sortBy: SortBy, val sortOrder: SortOrder) : LibraryAction

    data class OnSearchQueryChange(val query: String) : LibraryAction

    data object OnRefresh : LibraryAction

    data class ChangeFilter(val filter: MediaFilter) : LibraryAction

    data class OnSeerrRequest(val item: SeerrSearchItem) : LibraryAction

    data class OnSeerrCancelRequest(val request: SeerrRequestItem) : LibraryAction
}
