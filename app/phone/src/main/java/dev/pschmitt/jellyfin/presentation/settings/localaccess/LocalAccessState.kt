package dev.pschmitt.jellyfin.presentation.settings.localaccess

data class LocalAccessState(
    val localControlEnabled: Boolean = false,
    // Set only when the user just tried to turn local control on and the server actually failed
    // to start (e.g. the port is somehow already in use) - lets the toggle honestly reflect that
    // nothing is really listening instead of showing "enabled" while it's silently non-functional.
    val startFailed: Boolean = false,
    val token: String = "",
    // Populated in the ViewModel from LocalControlServer's own bind address/port/CLI path
    // constants, so this never drifts out of sync with what the server actually serves.
    val cliDownloadCommand: String = "",
)

sealed interface LocalAccessAction {
    data object OnBackClick : LocalAccessAction

    data class SetLocalControlEnabled(val enabled: Boolean) : LocalAccessAction

    data object RegenerateToken : LocalAccessAction

    data object CopyToken : LocalAccessAction

    data object CopyCliDownloadCommand : LocalAccessAction
}
