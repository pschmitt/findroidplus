package dev.pschmitt.jellyfin.film.presentation.media

import dev.pschmitt.jellyfin.models.JollyfinCollection

sealed interface MediaAction {
    data class OnItemClick(val item: JollyfinCollection) : MediaAction

    data object OnFavoritesClick : MediaAction

    data object OnRetryClick : MediaAction
}
