package dev.pschmitt.jellyfin.film.presentation.collection

import dev.pschmitt.jellyfin.models.FindroidItem

sealed interface CollectionAction {
    data class OnItemClick(val item: FindroidItem) : CollectionAction

    data object OnBackClick : CollectionAction
}
