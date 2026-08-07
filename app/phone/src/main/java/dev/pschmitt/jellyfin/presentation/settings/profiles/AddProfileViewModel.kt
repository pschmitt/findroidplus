package dev.pschmitt.jellyfin.presentation.settings.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.DiscoveredServer
import dev.pschmitt.jellyfin.models.ExceptionUiText
import dev.pschmitt.jellyfin.models.ExceptionUiTexts
import dev.pschmitt.jellyfin.models.ServerWithAddresses
import dev.pschmitt.jellyfin.models.UiText
import dev.pschmitt.jellyfin.models.User
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import dev.pschmitt.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A Jellyfin login (on some server) that doesn't have a Profile yet - eligible to create one. */
data class EligibleProfileUser(val user: User, val serverId: String, val serverName: String)

data class AddProfileState(
    val loading: Boolean = false,
    val eligibleUsers: List<EligibleProfileUser> = emptyList(),
    val creating: Boolean = false,
    // Inline "connect a new server, or log in as a new user on an existing one" flow - replaces
    // the old dead-end navigation to the (now-deleted) standalone Accounts/Connections screen.
    val servers: List<ServerWithAddresses> = emptyList(),
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    // The server currently targeted for a new login - either picked from [servers] or just
    // created via [AddProfileViewModel.addServer]. Non-null reveals the username/password +
    // QuickConnect form.
    val currentServerId: String? = null,
    val addServerError: UiText? = null,
    val loginError: UiText? = null,
    val quickConnectEnabled: Boolean = false,
    val quickConnectCode: String? = null,
    val operationInProgress: Boolean = false,
)

/**
 * Backs the "add profile" bottom sheet from [ProfilesListScreen] - kept separate from
 * [dev.pschmitt.jellyfin.setup.presentation.profiles.ProfilesViewModel] so that ViewModel stays
 * focused on listing/switching/managing existing profiles.
 *
 * This is the "onboard a new server" flow - unlike [ProfileDetailViewModel]'s user-reassignment
 * sub-flow, side effects on the globally active jellyfinApi/current profile are expected here:
 * [createProfile] always finishes by explicitly making the freshly created profile current.
 */
@HiltViewModel
class AddProfileViewModel
@Inject
constructor(
    private val setupRepository: SetupRepository,
    private val profileRepository: ProfileRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(AddProfileState())
    val state = _state.asStateFlow()

    private var quickConnectJob: Job? = null

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
                AddProfileState(loading = false, eligibleUsers = eligible, servers = servers)
        }
        discoverServers()
    }

    // Mirrors the discovery IntegrationsSettingsViewModel used to run - local-network mDNS
    // discovery so connecting a server from this sheet isn't a strictly worse experience than the
    // original onboarding flow, which offered this out of the box.
    private fun discoverServers() {
        viewModelScope.launch {
            val discovered = mutableListOf<DiscoveredServer>()
            setupRepository.discoverServers().collect { info ->
                discovered.add(DiscoveredServer(info.id, info.name, info.address))
                _state.value = _state.value.copy(discoveredServers = discovered)
            }
        }
    }

    fun createProfile(user: EligibleProfileUser, onCreated: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true)
            val profile = profileRepository.createProfile(user.user.id)
            profileRepository.setCurrentProfile(profile.id)
            _state.value = _state.value.copy(creating = false)
            onCreated()
        }
    }

    /** Selects an existing server as the target for a new login. */
    fun selectServer(serverId: String) {
        viewModelScope.launch {
            activateServer(serverId)
            _state.value = _state.value.copy(loginError = null)
        }
    }

    fun addServer(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(operationInProgress = true, addServerError = null)
            try {
                val server = setupRepository.addServer(address)
                activateServer(server.id)
                _state.value = _state.value.copy(servers = setupRepository.getServers())
            } catch (e: Exception) {
                _state.value = _state.value.copy(addServerError = e.toAddProfileUiText())
            } finally {
                _state.value = _state.value.copy(operationInProgress = false)
            }
        }
    }

    private suspend fun activateServer(serverId: String) {
        setupRepository.setCurrentServer(serverId)
        appPreferences.setValue(appPreferences.currentServer, serverId)
        val quickConnectEnabled =
            try {
                setupRepository.getIsQuickConnectEnabled()
            } catch (_: Exception) {
                false
            }
        _state.value =
            _state.value.copy(currentServerId = serverId, quickConnectEnabled = quickConnectEnabled)
    }

    fun login(username: String, password: String, onCreated: () -> Unit) {
        if (username.isBlank() || password.isBlank()) return
        quickConnectJob?.cancel()
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    operationInProgress = true,
                    loginError = null,
                    quickConnectCode = null,
                )
            try {
                setupRepository.login(username, password)
                finishOnboarding(onCreated)
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        operationInProgress = false,
                        loginError = e.toAddProfileUiText(),
                    )
            }
        }
    }

    fun quickConnect(onCreated: () -> Unit) {
        if (quickConnectJob?.isActive == true) {
            quickConnectJob?.cancel()
            _state.value = _state.value.copy(quickConnectCode = null)
            return
        }
        quickConnectJob = viewModelScope.launch {
            _state.value = _state.value.copy(loginError = null)
            try {
                var quickConnectState = setupRepository.initiateQuickConnect()
                _state.value = _state.value.copy(quickConnectCode = quickConnectState.code)

                while (!quickConnectState.authenticated) {
                    delay(5000L)
                    quickConnectState =
                        setupRepository.getQuickConnectState(quickConnectState.secret)
                }

                setupRepository.loginWithSecret(quickConnectState.secret)
                _state.value = _state.value.copy(quickConnectCode = null)
                finishOnboarding(onCreated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        quickConnectCode = null,
                        operationInProgress = false,
                        loginError = e.toAddProfileUiText(),
                    )
            }
        }
    }

    private suspend fun finishOnboarding(onCreated: () -> Unit) {
        val newUser = setupRepository.getCurrentUser()
        if (newUser != null) {
            val newProfile = profileRepository.createProfile(newUser.id)
            profileRepository.setCurrentProfile(newProfile.id)
        }
        _state.value = _state.value.copy(operationInProgress = false, creating = false)
        onCreated()
    }

    private fun Exception.toAddProfileUiText(): UiText =
        when (this) {
            is ExceptionUiText -> uiText
            is ExceptionUiTexts -> uiTexts.firstOrNull()
            else -> UiText.DynamicString(message ?: "")
        } ?: UiText.StringResource(CoreR.string.unknown_error)
}
