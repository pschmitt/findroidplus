package dev.jdtech.jellyfin.setup.presentation.backup

data class BackupSettingsState(
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalMinutes: Int = 24 * 60,
    val autoBackupFolderUri: String? = null,
    val autoBackupPassword: String? = null,
    val lastBackupTimestamp: Long = 0L,
    val isBackingUp: Boolean = false,
)
