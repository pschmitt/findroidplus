package dev.pschmitt.jellyfin.setup.presentation.profiles

import java.util.UUID

sealed interface ProfilesAction {
    data class OnProfileClick(val profileId: UUID) : ProfilesAction

    data class DeleteProfile(val profileId: UUID) : ProfilesAction

    data class SetMainProfile(val profileId: UUID) : ProfilesAction

    data class RenameProfile(val profileId: UUID, val name: String) : ProfilesAction

    data object OnAddClick : ProfilesAction

    data object OnBackClick : ProfilesAction
}
