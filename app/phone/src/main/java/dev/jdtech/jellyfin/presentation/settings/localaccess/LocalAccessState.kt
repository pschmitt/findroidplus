package dev.jdtech.jellyfin.presentation.settings.localaccess

import dev.jdtech.jellyfin.localcontrol.PairedClient

data class LocalAccessState(
    val isLoading: Boolean = false,
    val localControlEnabled: Boolean = false,
    val pairedClients: List<PairedClient> = emptyList(),
    val error: Exception? = null,
)

sealed interface LocalAccessAction {
    data object OnBackClick : LocalAccessAction

    data class SetLocalControlEnabled(val enabled: Boolean) : LocalAccessAction

    data class RevokeClient(val clientId: String) : LocalAccessAction
}
