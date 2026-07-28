package dev.pschmitt.jellyfin.presentation.settings.localaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.localcontrol.LocalControlAuth
import dev.pschmitt.jellyfin.localcontrol.LocalControlServer
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    localControlEnabled = enabledPref,
                    // The preference alone isn't proof the server actually bound its port (e.g. a
                    // prior start() may have silently failed) - surface that mismatch immediately
                    // rather than only after the user next flips the switch.
                    startFailed = enabledPref && !localControlServer.isRunning(),
                    token = localControlAuth.getOrCreateToken(),
                    cliDownloadCommand =
                        "curl http://${LocalControlServer.BIND_ADDRESS}:${LocalControlServer.PORT}" +
                            "${LocalControlServer.CLI_PATH} -o findroid-cli && chmod +x findroid-cli",
                )
            )
        }
    }

    fun onAction(action: LocalAccessAction) {
        when (action) {
            is LocalAccessAction.SetLocalControlEnabled -> setLocalControlEnabled(action.enabled)
            is LocalAccessAction.RegenerateToken -> regenerateToken()
            is LocalAccessAction.OnBackClick -> Unit
            is LocalAccessAction.CopyToken -> Unit
            is LocalAccessAction.CopyCliDownloadCommand -> Unit
        }
    }

    private fun setLocalControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Applies immediately, not just on next app start - the toggle would otherwise be
            // misleading (switched on, but nothing actually listening until a restart).
            val running =
                if (enabled) {
                    withContext(Dispatchers.IO) { localControlServer.startSafely() }
                } else {
                    withContext(Dispatchers.IO) { localControlServer.stop() }
                    true
                }
            // Don't persist "enabled" if the bind just failed - a future app start's
            // startIfEnabled() would only fail again the same way with nobody watching.
            val nowEnabled = enabled && running
            appPreferences.setValue(appPreferences.localControlEnabled, nowEnabled)
            _state.emit(
                _state.value.copy(localControlEnabled = nowEnabled, startFailed = enabled && !running)
            )
        }
    }

    private fun regenerateToken() {
        viewModelScope.launch {
            val token = localControlAuth.regenerateToken()
            _state.emit(_state.value.copy(token = token))
        }
    }
}
