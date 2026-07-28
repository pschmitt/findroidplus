package dev.pschmitt.jellyfin.settings.presentation.settings

import dev.pschmitt.jellyfin.settings.presentation.models.Preference

sealed interface SettingsAction {
    data object OnBackClick : SettingsAction

    data class OnUpdate(val preference: Preference) : SettingsAction

    data class OnRelocateDownloads(val mode: RelocateDownloadsMode, val from: String, val to: String) :
        SettingsAction
}

enum class RelocateDownloadsMode {
    MOVE,
    CLEAR,
}
