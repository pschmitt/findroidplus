package dev.jdtech.jellyfin.presentation.settings.localaccess

data class LocalAccessState(val localControlEnabled: Boolean = false, val token: String = "")

sealed interface LocalAccessAction {
    data object OnBackClick : LocalAccessAction

    data class SetLocalControlEnabled(val enabled: Boolean) : LocalAccessAction

    data object RegenerateToken : LocalAccessAction

    data object CopyToken : LocalAccessAction
}
