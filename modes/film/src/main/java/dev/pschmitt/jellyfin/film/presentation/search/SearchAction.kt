package dev.pschmitt.jellyfin.film.presentation.search

import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.SeerrSearchItem

sealed interface SearchAction {
    data class Search(val query: String) : SearchAction

    data class OnItemClick(val item: JollyfinItem) : SearchAction

    data class OnSeerrItemClick(val item: SeerrSearchItem) : SearchAction
}
