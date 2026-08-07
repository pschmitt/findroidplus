package dev.pschmitt.jellyfin.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.models.User
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import dev.pschmitt.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A Jellyfin login (on some server) that doesn't have a Profile yet - eligible to create one. */
data class EligibleProfileUser(val user: User, val serverId: String, val serverName: String)

data class AddProfileState(
    val loading: Boolean = false,
    val hasServers: Boolean = false,
    val eligibleUsers: List<EligibleProfileUser> = emptyList(),
    val creating: Boolean = false,
)

/**
 * TV mirror of app/phone's
 * `dev.pschmitt.jellyfin.presentation.settings.profiles.AddProfileViewModel`
 * - backs the "add profile" picker from [ProfilesScreen]. app/tv has no module dependency on
 *   app/phone, so this is duplicated rather than imported; both [SetupRepository] and
 *   [ProfileRepository] live in the shared `setup` module.
 */
@HiltViewModel
class AddProfileViewModel
@Inject
constructor(
    private val setupRepository: SetupRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddProfileState())
    val state = _state.asStateFlow()

    /** [existingUserIds] are filtered out - they already have a profile. */
    fun load(existingUserIds: Set<UUID>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val servers = setupRepository.getServers()
            val eligible = mutableListOf<EligibleProfileUser>()
            servers.forEach { server ->
                val users =
                    try {
                        setupRepository.getUsers(server.server.id)
                    } catch (_: Exception) {
                        emptyList()
                    }
                users
                    .filterNot { it.id in existingUserIds }
                    .forEach { user ->
                        eligible.add(
                            EligibleProfileUser(
                                user = user,
                                serverId = server.server.id,
                                serverName = server.server.name,
                            )
                        )
                    }
            }
            _state.value =
                AddProfileState(
                    loading = false,
                    hasServers = servers.isNotEmpty(),
                    eligibleUsers = eligible,
                )
        }
    }

    fun createProfile(user: EligibleProfileUser, onCreated: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true)
            profileRepository.createProfile(user.user.id)
            _state.value = _state.value.copy(creating = false)
            onCreated()
        }
    }
}
