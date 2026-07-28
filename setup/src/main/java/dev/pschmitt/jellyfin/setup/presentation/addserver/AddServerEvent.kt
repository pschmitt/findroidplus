package dev.pschmitt.jellyfin.setup.presentation.addserver

sealed interface AddServerEvent {
    data object Success : AddServerEvent
}
