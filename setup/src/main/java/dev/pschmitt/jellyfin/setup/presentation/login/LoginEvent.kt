package dev.pschmitt.jellyfin.setup.presentation.login

sealed interface LoginEvent {
    data object Success : LoginEvent
}
