package dev.jdtech.jellyfin.setup.presentation.backup

sealed interface BackupSettingsEvent {
    data object BackupNowSuccess : BackupSettingsEvent

    data class BackupNowError(val message: String?) : BackupSettingsEvent
}
