package dev.pschmitt.jellyfin.setup.presentation.profiles

import dev.pschmitt.jellyfin.models.ProfileWithUserAndServer
import java.util.UUID

data class ProfilesState(
    val profiles: List<ProfileWithUserAndServer> = emptyList(),
    val currentProfileId: UUID? = null,
) {
    val currentProfile: ProfileWithUserAndServer?
        get() = profiles.firstOrNull { it.profile.id == currentProfileId }
}
