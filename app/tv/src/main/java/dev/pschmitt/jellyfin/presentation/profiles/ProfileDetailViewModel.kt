package dev.pschmitt.jellyfin.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.api.pvr.RadarrApi
import dev.pschmitt.jellyfin.api.pvr.SeerrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrApi
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TV mirror of app/phone's
 * `dev.pschmitt.jellyfin.presentation.settings.profiles.ProfileDetailViewModel`
 * - app/tv has no module dependency on app/phone, so this ViewModel (and its [ProfileDetailState]/
 *   [ProfileDetailAction]) is duplicated here rather than imported. Everything it depends on
 *   ([ProfileRepository], [PvrConfigResolver], the PVR service APIs) lives in shared library
 *   modules (`setup`, `core`, `data`) that both apps already depend on, so the logic itself stays
 *   identical to phone's - only the app-module home changes. Keep in sync with the phone version if
 *   its behavior changes.
 */
@HiltViewModel
class ProfileDetailViewModel
@Inject
constructor(
    private val profileRepository: ProfileRepository,
    private val pvrConfigResolver: PvrConfigResolver,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileDetailState())
    val state = _state.asStateFlow()

    private var profileId: UUID? = null

    // Text fields (base URL, API key, advanced HTTP settings) are debounced instead of persisted
    // per keystroke - every write goes through ProfileRepository.overridePvrConfig, which touches
    // Room + encrypted secrets. Dirty flags let onCleared flush anything still pending when the
    // screen closes mid-debounce.
    private val persistJobs = mutableMapOf<PvrService, Job>()
    private val dirtyServices = mutableSetOf<PvrService>()

    // ProfileRepository's methods are suspend (Room), so a flush issued from onCleared can't ride
    // viewModelScope - it's already cancelled by the time onCleared runs. This tiny scope exists
    // solely to let that last write complete, then cancels itself.
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun load(profileIdString: String) {
        val id = runCatching { UUID.fromString(profileIdString) }.getOrNull() ?: return
        profileId = id
        viewModelScope.launch { loadState(id) }
    }

    private suspend fun loadState(id: UUID) {
        val profile = profileRepository.getProfiles().firstOrNull { it.profile.id == id } ?: return
        _state.value =
            ProfileDetailState(
                loading = false,
                name = profile.profile.name,
                isMain = profile.profile.isMain,
                sonarr = loadSection(id, PvrService.SONARR),
                radarr = loadSection(id, PvrService.RADARR),
                seerr = loadSection(id, PvrService.SEERR),
            )
    }

    private suspend fun loadSection(id: UUID, service: PvrService): PvrSectionState {
        val inheriting = profileRepository.isInheriting(id, service)
        val resolved = pvrConfigResolver.resolveConfigForProfile(id, service)
        return PvrSectionState(
            inheriting = inheriting,
            enabled = resolved?.enabled ?: false,
            baseUrl = resolved?.baseUrl.orEmpty(),
            apiKey = resolved?.apiKey.orEmpty(),
            httpHeaders = resolved?.httpHeaders.orEmpty(),
            basicAuthUsername = resolved?.basicAuthUsername.orEmpty(),
            basicAuthPassword = resolved?.basicAuthPassword.orEmpty(),
        )
    }

    fun onAction(action: ProfileDetailAction) {
        val id = profileId ?: return
        when (action) {
            is ProfileDetailAction.OnBackClick -> Unit
            is ProfileDetailAction.OnRenameConfirmed -> renameProfile(id, action.name)
            is ProfileDetailAction.OnSetAsMainClick -> setAsMain(id)
            is ProfileDetailAction.OnDeleteClick -> deleteProfile(id)
            is ProfileDetailAction.OnToggleInherit ->
                toggleInherit(id, action.service, action.inherit)
            is ProfileDetailAction.OnEnabledChanged -> {
                updateSection(action.service) { it.copy(enabled = action.enabled) }
                persistImmediately(id, action.service)
            }
            is ProfileDetailAction.OnBaseUrlChanged -> {
                updateSection(action.service) {
                    it.copy(baseUrl = action.baseUrl, testState = PvrTestState.Idle)
                }
                persistDebounced(id, action.service)
            }
            is ProfileDetailAction.OnApiKeyChanged -> {
                updateSection(action.service) {
                    it.copy(apiKey = action.apiKey, testState = PvrTestState.Idle)
                }
                persistDebounced(id, action.service)
            }
            is ProfileDetailAction.OnAdvancedSettingsChanged -> {
                updateSection(action.service) {
                    it.copy(
                        httpHeaders = action.headers,
                        basicAuthUsername = action.basicAuthUsername,
                        basicAuthPassword = action.basicAuthPassword,
                    )
                }
                persistDebounced(id, action.service)
            }
            is ProfileDetailAction.OnTestConnectionClick -> testConnection(action.service)
        }
    }

    private fun renameProfile(id: UUID, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            profileRepository.renameProfile(id, name)
            _state.value = _state.value.copy(name = name)
        }
    }

    private fun setAsMain(id: UUID) {
        viewModelScope.launch {
            profileRepository.setMainProfile(id)
            // Becoming main changes what every section resolves to (main no longer inherits), so
            // reload everything rather than patching isMain alone.
            loadState(id)
        }
    }

    private fun deleteProfile(id: UUID) {
        // The main profile can't be deleted (ProfileRepository no-ops it) - the UI already
        // disables the action, this is just a guard against a stray call.
        if (_state.value.isMain) return
        viewModelScope.launch {
            profileRepository.deleteProfile(id)
            _state.value = _state.value.copy(deleted = true)
        }
    }

    private fun toggleInherit(id: UUID, service: PvrService, inherit: Boolean) {
        persistJobs[service]?.cancel()
        dirtyServices.remove(service)
        viewModelScope.launch {
            if (inherit) {
                profileRepository.reinheritPvrConfig(id, service)
                val section = loadSection(id, service)
                _state.value = _state.value.withSection(service, section)
            } else {
                // Seed the override with whatever's currently shown (the inherited preview)
                // instead of silently blanking the fields when switching to "custom".
                val seeded = _state.value.section(service).copy(inheriting = false)
                _state.value = _state.value.withSection(service, seeded)
                flushSection(id, service)
            }
        }
    }

    private fun updateSection(
        service: PvrService,
        transform: (PvrSectionState) -> PvrSectionState,
    ) {
        val updated = transform(_state.value.section(service))
        _state.value = _state.value.withSection(service, updated)
    }

    private fun persistImmediately(id: UUID, service: PvrService) {
        persistJobs[service]?.cancel()
        dirtyServices.remove(service)
        viewModelScope.launch { flushSection(id, service) }
    }

    private fun persistDebounced(id: UUID, service: PvrService) {
        dirtyServices.add(service)
        persistJobs[service]?.cancel()
        persistJobs[service] = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            flushSection(id, service)
        }
    }

    private suspend fun flushSection(id: UUID, service: PvrService) {
        val section = _state.value.section(service)
        profileRepository.overridePvrConfig(
            profileId = id,
            service = service,
            enabled = section.enabled,
            baseUrl = section.baseUrl.ifBlank { null },
            apiKey = section.apiKey.ifBlank { null },
            httpHeaders = section.httpHeaders.ifBlank { null },
            basicAuthUsername = section.basicAuthUsername.ifBlank { null },
            basicAuthPassword = section.basicAuthPassword.ifBlank { null },
        )
        dirtyServices.remove(service)
    }

    private fun testConnection(service: PvrService) {
        val section = _state.value.section(service)
        val baseUrl = section.baseUrl
        val apiKey = section.apiKey
        updateSection(service) { it.copy(testState = PvrTestState.Testing) }
        viewModelScope.launch {
            val result =
                try {
                    when (service) {
                        PvrService.SONARR ->
                            PvrTestState.Success(SonarrApi(baseUrl, apiKey).getSeries().size)
                        PvrService.RADARR ->
                            PvrTestState.Success(RadarrApi(baseUrl, apiKey).getMovie().size)
                        PvrService.SEERR -> {
                            val api = SeerrApi(baseUrl, apiKey)
                            // auth/me validates the key; the request count doubles as the "N
                            // items" the shared success message expects.
                            api.getCurrentUser()
                            PvrTestState.Success(api.getRequests(take = 1).pageInfo.results)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PvrTestState.Error(e.message ?: e.toString())
                }
            updateSection(service) { it.copy(testState = result) }
        }
    }

    private fun ProfileDetailState.section(service: PvrService): PvrSectionState =
        when (service) {
            PvrService.SONARR -> sonarr
            PvrService.RADARR -> radarr
            PvrService.SEERR -> seerr
        }

    private fun ProfileDetailState.withSection(
        service: PvrService,
        section: PvrSectionState,
    ): ProfileDetailState =
        when (service) {
            PvrService.SONARR -> copy(sonarr = section)
            PvrService.RADARR -> copy(radarr = section)
            PvrService.SEERR -> copy(seerr = section)
        }

    override fun onCleared() {
        super.onCleared()
        persistJobs.values.forEach { it.cancel() }
        val id = profileId
        val pending = dirtyServices.toSet()
        if (id != null && pending.isNotEmpty()) {
            flushScope.launch {
                pending.forEach { service -> flushSection(id, service) }
                flushScope.cancel()
            }
        } else {
            flushScope.cancel()
        }
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 750L
    }
}
