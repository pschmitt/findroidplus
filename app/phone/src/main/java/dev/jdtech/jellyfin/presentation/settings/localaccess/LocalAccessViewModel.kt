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
            _state.emit(
                _state.value.copy(
                    isLoading = true,
                    error = null,
                    localControlEnabled = appPreferences.getValue(appPreferences.localControlEnabled),
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
            if (enabled) localControlServer.start() else localControlServer.stop()
            _state.emit(_state.value.copy(localControlEnabled = enabled))
        }
    }

    private fun revokeClient(clientId: String) {
        viewModelScope.launch {
            localControlAuth.revoke(clientId)
            load()
        }
    }
}
