package dev.pschmitt.jellyfin.pvr

import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.Profile
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedPvrConfig(
    val configId: UUID,
    val enabled: Boolean,
    val baseUrl: String?,
    val apiKey: String?,
    val httpHeaders: String?,
    val basicAuthUsername: String?,
    val basicAuthPassword: String?,
)

/**
 * Resolves the effective Sonarr/Radarr/Seerr config for a Profile, replacing the pre-Profiles
 * global [AppPreferences] PVR fields + fixed [PvrCredentialKeys] as the single source of truth
 * every PVR consumer reads from. A profile's `sonarrConfigId`/`radarrConfigId`/`seerrConfigId`
 * being null means "inherit from whichever profile is currently main" - resolved dynamically here
 * on every call rather than copied at profile-creation time, so transferring "main" status to a
 * different profile automatically updates every still-inheriting profile with no extra step.
 *
 * Plain synchronous methods, matching [ServerDatabaseDao] (Room is built with
 * `allowMainThreadQueries()`) and the pre-existing [PvrConfiguration] - no coroutines needed.
 */
@Singleton
class PvrConfigResolver
@Inject
constructor(
    private val dao: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val secureCredentialStore: SecureCredentialStore,
) {
    /** Resolves [service] for the currently active profile ([AppPreferences.currentProfileId]). */
    fun resolveConfig(service: PvrService): ResolvedPvrConfig? {
        val profile = currentProfile() ?: return null
        return resolveConfigForProfile(profile, service)
    }

    /** Resolves [service] for a specific profile, e.g. to preview it in the profile-detail UI. */
    fun resolveConfigForProfile(profileId: UUID, service: PvrService): ResolvedPvrConfig? {
        val profile = dao.getProfile(profileId) ?: return null
        return resolveConfigForProfile(profile, service)
    }

    /** True if [service] is enabled and has both a base URL and an API key resolved. */
    fun isConfigured(service: PvrService): Boolean {
        val config = resolveConfig(service) ?: return false
        return config.enabled && !config.baseUrl.isNullOrBlank() && !config.apiKey.isNullOrBlank()
    }

    fun currentProfile(): Profile? {
        val id =
            appPreferences.getValue(appPreferences.currentProfileId)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return null
        return dao.getProfile(id)
    }

    private fun resolveConfigForProfile(profile: Profile, service: PvrService): ResolvedPvrConfig? {
        val ownConfigId = configIdFor(profile, service)
        val configId =
            ownConfigId ?: dao.getMainProfile()?.let { configIdFor(it, service) } ?: return null
        val config = dao.getPvrServiceConfig(configId) ?: return null
        return ResolvedPvrConfig(
            configId = configId,
            enabled = config.enabled,
            baseUrl = config.baseUrl,
            apiKey = secureCredentialStore.getString(PvrCredentialKeys.apiKey(service, configId)),
            httpHeaders =
                secureCredentialStore.getString(PvrCredentialKeys.httpHeaders(service, configId)),
            basicAuthUsername =
                secureCredentialStore.getString(
                    PvrCredentialKeys.basicAuthUsername(service, configId)
                ),
            basicAuthPassword =
                secureCredentialStore.getString(
                    PvrCredentialKeys.basicAuthPassword(service, configId)
                ),
        )
    }

    private fun configIdFor(profile: Profile, service: PvrService): UUID? =
        when (service) {
            PvrService.SONARR -> profile.sonarrConfigId
            PvrService.RADARR -> profile.radarrConfigId
            PvrService.SEERR -> profile.seerrConfigId
        }
}
