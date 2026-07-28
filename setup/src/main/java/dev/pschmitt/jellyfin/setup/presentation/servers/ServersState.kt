package dev.pschmitt.jellyfin.setup.presentation.servers

import dev.pschmitt.jellyfin.models.ServerWithAddresses

data class ServersState(val servers: List<ServerWithAddresses> = emptyList())
