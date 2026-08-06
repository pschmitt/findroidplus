package dev.pschmitt.jellyfin.film.presentation.collection

import dev.pschmitt.jellyfin.models.JollyfinItem

sealed interface CollectionAction {
    data class OnItemClick(val item: JollyfinItem) : CollectionAction

    data object OnBackClick : CollectionAction
}
