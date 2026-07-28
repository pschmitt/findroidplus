package dev.pschmitt.jellyfin.setup.presentation.users

import dev.pschmitt.jellyfin.models.User

data class UsersState(
    val users: List<User> = emptyList(),
    val publicUsers: List<User> = emptyList(),
    val serverName: String? = null,
)
