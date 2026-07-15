package dev.jdtech.jellyfin.setup.presentation.restore

import android.net.Uri

sealed interface RestoreBackupAction {
    data class OnFilePicked(val uri: Uri) : RestoreBackupAction

    data class OnPasswordSubmit(val password: String) : RestoreBackupAction

    data object OnRedownloadYes : RestoreBackupAction

    data object OnRedownloadNo : RestoreBackupAction

    data object OnBackClick : RestoreBackupAction
}
