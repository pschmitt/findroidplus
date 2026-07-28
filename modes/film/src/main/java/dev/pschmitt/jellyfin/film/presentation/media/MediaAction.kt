package dev.pschmitt.jellyfin.film.presentation.media

import dev.pschmitt.jellyfin.models.FindroidCollection

sealed interface MediaAction {
    data class OnItemClick(val item: FindroidCollection) : MediaAction

    data object OnFavoritesClick : MediaAction

    data object OnRetryClick : MediaAction
}
