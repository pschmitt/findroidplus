package dev.pschmitt.jellyfin.film.presentation.home

import dev.pschmitt.jellyfin.models.FindroidCollection
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.SeerrSearchItem

sealed interface HomeAction {
    data class OnItemClick(val item: FindroidItem) : HomeAction

    /** A discovery-row item - not in the library, opens the Seerr media detail view. */
    data class OnSeerrItemClick(val item: SeerrSearchItem) : HomeAction

    data class OnLibraryClick(val library: FindroidCollection) : HomeAction

    data object OnRetryClick : HomeAction

    data object OnEnableOfflineMode : HomeAction

    data object OnSettingsClick : HomeAction

    data object OnManageServers : HomeAction

    /** Long-press drag reorder of a Home section, straight from the Home screen itself. */
    data class OnReorderSections(val fromIndex: Int, val toIndex: Int) : HomeAction
}
