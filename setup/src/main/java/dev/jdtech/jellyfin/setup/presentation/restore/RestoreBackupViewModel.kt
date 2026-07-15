package dev.jdtech.jellyfin.setup.presentation.restore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.backup.BackupCrypto
import dev.jdtech.jellyfin.backup.BackupManager
import dev.jdtech.jellyfin.backup.encodePendingRestoreDownloads
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RestoreBackupViewModel
@Inject
constructor(
    private val backupManager: BackupManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(RestoreBackupState())
    val state = _state.asStateFlow()

    private var pickedUri: Uri? = null

    fun onAction(action: RestoreBackupAction) {
        when (action) {
            is RestoreBackupAction.OnFilePicked -> {
                pickedUri = action.uri
                loadBackup(password = null)
            }
            is RestoreBackupAction.OnPasswordSubmit -> {
                loadBackup(password = action.password)
            }
            is RestoreBackupAction.OnRedownloadYes -> {
                val items = _state.value.summary?.downloadedItems.orEmpty()
                appPreferences.setValue(
                    appPreferences.pendingRestoreDownloads,
                    encodePendingRestoreDownloads(items),
                )
                _state.value = _state.value.copy(downloadPromptAnswered = true)
            }
            is RestoreBackupAction.OnRedownloadNo -> {
                _state.value = _state.value.copy(downloadPromptAnswered = true)
            }
            is RestoreBackupAction.OnBackClick -> Unit
        }
    }

    private fun loadBackup(password: String?) {
        val uri = pickedUri ?: return
        viewModelScope.launch {
            _state.value =
                _state.value.copy(isLoading = true, error = null, wrongPassword = false)
            try {
                val envelope = backupManager.readBackup(uri, password)
                val summary = backupManager.restore(envelope)
                _state.value =
                    _state.value.copy(isLoading = false, needsPassword = false, summary = summary)
            } catch (e: BackupCrypto.WrongPasswordException) {
                _state.value =
                    _state.value.copy(isLoading = false, needsPassword = true, wrongPassword = true)
            } catch (e: BackupCrypto.PasswordRequiredException) {
                _state.value = _state.value.copy(isLoading = false, needsPassword = true)
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(isLoading = false, error = e.message ?: "Restore failed")
            }
        }
    }
}
