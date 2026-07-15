package dev.jdtech.jellyfin.setup.presentation.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.backup.BackupManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.AutoBackupScheduler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BackupSettingsViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupSettingsState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<BackupSettingsEvent>()
    val events = eventsChannel.receiveAsFlow()

    fun load() {
        _state.value =
            BackupSettingsState(
                autoBackupEnabled = appPreferences.getValue(appPreferences.autoBackupEnabled),
                autoBackupIntervalMinutes =
                    appPreferences.getValue(appPreferences.autoBackupIntervalMinutes),
                autoBackupFolderUri = appPreferences.getValue(appPreferences.autoBackupFolderUri),
                lastBackupTimestamp = appPreferences.getValue(appPreferences.lastBackupTimestamp),
            )
    }

    private fun reschedule() {
        AutoBackupScheduler.schedule(context, appPreferences)
    }

    fun onAction(action: BackupSettingsAction) {
        when (action) {
            is BackupSettingsAction.OnAutoBackupEnabledChanged -> {
                appPreferences.setValue(appPreferences.autoBackupEnabled, action.enabled)
                reschedule()
                load()
            }
            is BackupSettingsAction.OnAutoBackupIntervalChanged -> {
                appPreferences.setValue(appPreferences.autoBackupIntervalMinutes, action.minutes)
                reschedule()
                load()
            }
            is BackupSettingsAction.OnFolderPicked -> {
                appPreferences.setValue(appPreferences.autoBackupFolderUri, action.uri.toString())
                reschedule()
                load()
            }
            is BackupSettingsAction.OnBackupNow -> {
                viewModelScope.launch {
                    _state.value = _state.value.copy(isBackingUp = true)
                    try {
                        val envelope = backupManager.buildBackup()
                        backupManager.writeBackup(envelope, action.uri, action.password)
                        appPreferences.setValue(
                            appPreferences.lastBackupTimestamp,
                            System.currentTimeMillis(),
                        )
                        eventsChannel.send(BackupSettingsEvent.BackupNowSuccess)
                    } catch (e: Exception) {
                        eventsChannel.send(BackupSettingsEvent.BackupNowError(e.message))
                    }
                    _state.value = _state.value.copy(isBackingUp = false)
                }
            }
            is BackupSettingsAction.OnBackClick -> Unit
        }
    }
}
