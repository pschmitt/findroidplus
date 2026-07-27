package dev.jdtech.jellyfin.setup.presentation.qrexport

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

    data class OnPasswordChanged(val password: String) : QrExportAction

    data object OnGenerateClick : QrExportAction

    data object OnBackClick : QrExportAction
}
