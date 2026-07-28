package dev.jdtech.jellyfin.presentation.settings.localaccess

import dev.jdtech.jellyfin.localcontrol.PairedClient

data class LocalAccessState(
    val isLoading: Boolean = false,
    val localControlEnabled: Boolean = false,
    // Set only when the user just tried to turn local control on and the socket bind actually
    // failed (e.g. already bound by something else) - lets the toggle honestly reflect that
    // nothing is really listening instead of showing "enabled" while local control is silently
    // non-functional.
    val startFailed: Boolean = false,
    val pairedClients: List<PairedClient> = emptyList(),
    val error: Exception? = null,
)

sealed interface LocalAccessAction {
    data object OnBackClick : LocalAccessAction

    data class SetLocalControlEnabled(val enabled: Boolean) : LocalAccessAction

    data class RevokeClient(val clientId: String) : LocalAccessAction
}
