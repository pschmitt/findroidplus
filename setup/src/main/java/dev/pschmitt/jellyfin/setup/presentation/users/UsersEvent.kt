package dev.pschmitt.jellyfin.setup.presentation.users

sealed interface UsersEvent {
    data object NavigateToHome : UsersEvent
}
