package dev.pschmitt.jellyfin.models

import androidx.room.Embedded

data class ProfileWithUserAndServer(
    @Embedded val profile: Profile,
    val userName: String,
    val serverId: String,
    val serverName: String,
)
