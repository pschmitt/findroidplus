package dev.jdtech.jellyfin.setup.presentation.restore

import dev.jdtech.jellyfin.backup.RestoreSummary

data class RestoreBackupState(
    val isLoading: Boolean = false,
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val error: String? = null,
    val summary: RestoreSummary? = null,
    val downloadPromptAnswered: Boolean = false,
)
