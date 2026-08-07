package dev.pschmitt.jellyfin.setup.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfilesViewModel @Inject constructor(private val repository: ProfileRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(ProfilesState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ProfilesEvent>()
    val events = eventsChannel.receiveAsFlow()

    fun loadProfiles() {
        viewModelScope.launch {
            val profiles = repository.getProfiles()
            val currentProfileId = repository.getCurrentProfile()?.profile?.id
            _state.emit(ProfilesState(profiles = profiles, currentProfileId = currentProfileId))
        }
    }

    private fun setCurrentProfile(profileId: UUID) {
        viewModelScope.launch {
            repository.setCurrentProfile(profileId)
            eventsChannel.send(ProfilesEvent.ProfileChanged)
        }
    }

    fun onAction(action: ProfilesAction) {
        when (action) {
            is ProfilesAction.OnProfileClick -> setCurrentProfile(action.profileId)
            is ProfilesAction.DeleteProfile -> {
                viewModelScope.launch {
                    repository.deleteProfile(action.profileId)
                    loadProfiles()
                }
            }
            is ProfilesAction.SetMainProfile -> {
                viewModelScope.launch {
                    repository.setMainProfile(action.profileId)
                    loadProfiles()
                }
            }
            is ProfilesAction.RenameProfile -> {
                viewModelScope.launch {
                    repository.renameProfile(action.profileId, action.name)
                    loadProfiles()
                }
            }
            else -> Unit
        }
    }
}
