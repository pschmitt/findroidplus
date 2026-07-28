package dev.pschmitt.jellyfin.setup.presentation.addresses

import dev.pschmitt.jellyfin.models.ServerAddress

data class ServerAddressesState(val addresses: List<ServerAddress> = emptyList())
