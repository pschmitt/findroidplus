package dev.pschmitt.jellyfin.film.presentation.person

import dev.pschmitt.jellyfin.models.FindroidItem

sealed interface PersonAction {
    data object NavigateBack : PersonAction

    data object NavigateHome : PersonAction

    data object NavigateToSettings : PersonAction

    data class NavigateToItem(val item: FindroidItem) : PersonAction
}
