package dev.pschmitt.jellyfin.setup.presentation.qrexport

import java.util.UUID

sealed interface QrExportAction {
    data object OnLoad : QrExportAction

    data class OnIncludeJellyfinChanged(val include: Boolean) : QrExportAction

    data class OnIncludeSonarrChanged(val include: Boolean) : QrExportAction

    data class OnIncludeRadarrChanged(val include: Boolean) : QrExportAction

    data class OnIncludeSeerrChanged(val include: Boolean) : QrExportAction

    data class OnServerSelected(val serverId: String) : QrExportAction

    data class OnUserSelected(val userId: UUID) : QrExportAction

    data object OnAdvancedToggle : QrExportAction

    data class OnJellyfinUsernameChanged(val value: String) : QrExportAction

    data class OnJellyfinPasswordChanged(val value: String) : QrExportAction

    data object OnToggleJellyfinPasswordVisibility : QrExportAction

    data class OnSonarrBaseUrlChanged(val value: String) : QrExportAction

    data class OnSonarrApiKeyChanged(val value: String) : QrExportAction

    data class OnRadarrBaseUrlChanged(val value: String) : QrExportAction

    data class OnRadarrApiKeyChanged(val value: String) : QrExportAction

    data class OnSeerrBaseUrlChanged(val value: String) : QrExportAction

    data class OnSeerrApiKeyChanged(val value: String) : QrExportAction

    data object OnRegeneratePassword : QrExportAction

    data object OnTogglePasswordVisibility : QrExportAction

    data object OnBackClick : QrExportAction
}
