package dev.pschmitt.jellyfin.setup.presentation.profiles

sealed interface ProfilesEvent {
    data object ProfileChanged : ProfilesEvent
}
