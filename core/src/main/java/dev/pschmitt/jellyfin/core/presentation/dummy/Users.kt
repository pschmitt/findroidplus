package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.User
import java.util.UUID

val dummyUser = User(id = UUID.randomUUID(), name = "Username", serverId = "")

val dummyUsers = listOf(dummyUser)
