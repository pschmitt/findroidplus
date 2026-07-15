package dev.jdtech.jellyfin.setup.presentation.backup

import android.net.Uri

sealed interface BackupSettingsAction {
    data object OnBackClick : BackupSettingsAction

    data class OnAutoBackupEnabledChanged(val enabled: Boolean) : BackupSettingsAction

    data class OnAutoBackupIntervalChanged(val minutes: Int) : BackupSettingsAction

    data class OnFolderPicked(val uri: Uri) : BackupSettingsAction

    data class OnBackupNow(val uri: Uri, val password: String?) : BackupSettingsAction
}
