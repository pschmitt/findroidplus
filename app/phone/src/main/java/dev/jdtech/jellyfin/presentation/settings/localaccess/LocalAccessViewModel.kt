package dev.jdtech.jellyfin.presentation.settings.localaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.localcontrol.LocalControlAuth
import dev.jdtech.jellyfin.localcontrol.LocalControlServer
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LocalAccessViewModel
@Inject
constructor(
    private val localControlAuth: LocalControlAuth,
    private val localControlServer: LocalControlServer,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalAccessState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val enabledPref = appPreferences.getValue(appPreferences.localControlEnabled)
            _state.emit(
                _state.value.copy(
                    isLoading = true,
                    error = null,
                    localControlEnabled = enabledPref,
                    // The preference alone isn't proof anything is actually listening (e.g. a
                    // previous start() may have silently failed to bind) - surface that mismatch
                    // immediately rather than only after the user next flips the switch.
                    startFailed = enabledPref && !localControlServer.isRunning(),
                )
            )
            try {
                val clients = localControlAuth.listPairedClients()
                _state.emit(_state.value.copy(isLoading = false, pairedClients = clients))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    fun onAction(action: LocalAccessAction) {
        when (action) {
            is LocalAccessAction.SetLocalControlEnabled -> setLocalControlEnabled(action.enabled)
            is LocalAccessAction.RevokeClient -> revokeClient(action.clientId)
            is LocalAccessAction.OnBackClick -> Unit
        }
    }

    private fun setLocalControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setValue(appPreferences.localControlEnabled, enabled)
            // Applies immediately, not just on next app start - the toggle would otherwise be
            // misleading (switched on, but nothing actually listening until a restart).
            val running =
                if (enabled) {
                    localControlServer.start()
                } else {
                    localControlServer.stop()
                    true
                }
            _state.emit(
                _state.value.copy(localControlEnabled = enabled, startFailed = enabled && !running)
            )
        }
    }

    private fun revokeClient(clientId: String) {
        viewModelScope.launch {
            localControlAuth.revoke(clientId)
            load()
        }
    }
}
