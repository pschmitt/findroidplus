package dev.jdtech.jellyfin.presentation.settings.localaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.localcontrol.LocalControlAuth
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
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalAccessState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    localControlEnabled = appPreferences.getValue(appPreferences.localControlEnabled),
                    token = localControlAuth.getOrCreateToken(),
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
        }
    }

    private fun setLocalControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setValue(appPreferences.localControlEnabled, enabled)
            _state.emit(_state.value.copy(localControlEnabled = enabled))
        }
    }

    private fun regenerateToken() {
        viewModelScope.launch {
            val token = localControlAuth.regenerateToken()
            _state.emit(_state.value.copy(token = token))
        }
    }
}
