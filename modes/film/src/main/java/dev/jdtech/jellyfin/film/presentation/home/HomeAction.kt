package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.SeerrSearchItem

sealed interface HomeAction {
    data class OnItemClick(val item: FindroidItem) : HomeAction

    /** A discovery-row item - not in the library, opens the Seerr media detail view. */
    data class OnSeerrItemClick(val item: SeerrSearchItem) : HomeAction

    data class OnLibraryClick(val library: FindroidCollection) : HomeAction

    data object OnRetryClick : HomeAction

    data object OnFavoritesClick : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnManageServers : HomeAction
}
