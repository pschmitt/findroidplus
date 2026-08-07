package dev.pschmitt.jellyfin.setup.data

import dev.pschmitt.jellyfin.api.JellyfinApi
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.Profile
import dev.pschmitt.jellyfin.models.ProfileWithUserAndServer
import dev.pschmitt.jellyfin.models.PvrServiceConfig
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import java.util.UUID

/**
 * Lives in `setup` (not `data`, unlike most other repositories) because it needs both
 * [ServerDatabaseDao]/[dev.pschmitt.jellyfin.api.JellyfinApi] (from `data`) and
 * [SecureCredentialStore] (from `core`) at once - `data` cannot depend on `core`, and `setup` is
 * the closest module that depends on both, same reasoning
 * [dev.pschmitt.jellyfin.pvr.PvrConfigResolver] follows for reading this data back out.
 */
class ProfileRepositoryImpl(
    private val jellyfinApi: JellyfinApi,
    private val dao: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val secureCredentialStore: SecureCredentialStore,
) : ProfileRepository {
    override suspend fun getProfiles(): List<ProfileWithUserAndServer> =
        dao.getProfilesWithUserAndServer()

    override suspend fun getCurrentProfile(): ProfileWithUserAndServer? {
        val id = currentProfileId() ?: return null
        return dao.getProfileWithUserAndServer(id)
    }

    override suspend fun createProfile(userId: UUID, name: String?): Profile {
        val user = dao.getUser(userId) ?: throw IllegalArgumentException("Unknown user id: $userId")
        val server = dao.get(user.serverId)
        val profile =
            Profile(
                id = UUID.randomUUID(),
                name = name ?: "${user.name}@${server?.name ?: user.serverId}",
                userId = userId,
                // The very first profile ever created (fresh install, first Jellyfin login) has
                // no other profile to inherit PVR config from, so it becomes main automatically.
                isMain = dao.getMainProfile() == null,
            )
        dao.insertProfile(profile)
        return profile
    }

    override suspend fun renameProfile(profileId: UUID, name: String) {
        val profile = dao.getProfile(profileId) ?: return
        profile.name = name
        dao.updateProfile(profile)
    }

    override suspend fun setProfileUser(profileId: UUID, userId: UUID) {
        val profile = dao.getProfile(profileId) ?: return
        profile.userId = userId
        dao.updateProfile(profile)

        if (profileId != currentProfileId()) return

        val user = dao.getUser(userId) ?: return
        val server = dao.get(user.serverId) ?: return

        // Keep the legacy dual-tracked current-server/user in sync - mirrors
        // SetupRepositoryImpl.setCurrentUser (only the user/access token changes here, the server
        // and its address stay the same).
        dao.updateServerCurrentUser(server.id, user.id)
        jellyfinApi.apply {
            api.update(accessToken = user.accessToken)
            this.userId = user.id
        }
    }

    override suspend fun setMainProfile(profileId: UUID) {
        val newMain = dao.getProfile(profileId) ?: return
        if (newMain.isMain) return
        val oldMain = dao.getMainProfile()
        if (oldMain != null) {
            // The new main can no longer "inherit from main" once it becomes main itself - adopt
            // whatever it was resolving to (its own override, or the old main's config) as its own
            // explicit, non-null pointer, so every profile still inheriting keeps seeing the same
            // config rows as before the transfer.
            newMain.sonarrConfigId = newMain.sonarrConfigId ?: oldMain.sonarrConfigId
            newMain.radarrConfigId = newMain.radarrConfigId ?: oldMain.radarrConfigId
            newMain.seerrConfigId = newMain.seerrConfigId ?: oldMain.seerrConfigId
            oldMain.isMain = false
            dao.updateProfile(oldMain)
        }
        newMain.isMain = true
        dao.updateProfile(newMain)
    }

    override suspend fun deleteProfile(profileId: UUID) {
        val profile = dao.getProfile(profileId) ?: return
        if (profile.isMain) return
        for (service in PvrService.entries) {
            val configId = configIdFor(profile, service) ?: continue
            dao.deletePvrServiceConfig(configId)
            removeSecrets(service, configId)
        }
        dao.deleteProfile(profileId)
    }

    override suspend fun setCurrentProfile(profileId: UUID) {
        val profile = dao.getProfile(profileId) ?: return
        val user = dao.getUser(profile.userId) ?: return
        val server = dao.get(user.serverId) ?: return
        val address =
            dao.getServerCurrentAddress(server.id)
                ?: dao.getServerWithAddresses(server.id).addresses.firstOrNull()
                ?: return

        // Keep the legacy dual-tracked current-server/user in sync - still relied on elsewhere
        // (e.g. IntegrationsSettingsViewModel, backup/QR export of the Jellyfin connection itself).
        dao.updateServerCurrentUser(server.id, user.id)
        jellyfinApi.apply {
            api.update(baseUrl = address.address, accessToken = user.accessToken)
            userId = user.id
        }
        appPreferences.setValue(appPreferences.currentServer, server.id)
        appPreferences.setValue(appPreferences.currentProfileId, profileId.toString())
    }

    override suspend fun isInheriting(profileId: UUID, service: PvrService): Boolean {
        val profile = dao.getProfile(profileId) ?: return false
        return configIdFor(profile, service) == null
    }

    override suspend fun overridePvrConfig(
        profileId: UUID,
        service: PvrService,
        enabled: Boolean,
        baseUrl: String?,
        apiKey: String?,
        httpHeaders: String?,
        basicAuthUsername: String?,
        basicAuthPassword: String?,
    ) {
        val profile = dao.getProfile(profileId) ?: return
        val existingConfigId = configIdFor(profile, service)
        val configId = existingConfigId ?: UUID.randomUUID()
        val config =
            PvrServiceConfig(id = configId, service = service, enabled = enabled, baseUrl = baseUrl)

        if (existingConfigId != null) {
            dao.updatePvrServiceConfig(config)
        } else {
            dao.insertPvrServiceConfig(config)
            setConfigIdFor(profile, service, configId)
            dao.updateProfile(profile)
        }

        secureCredentialStore.putString(PvrCredentialKeys.apiKey(service, configId), apiKey)
        secureCredentialStore.putString(
            PvrCredentialKeys.httpHeaders(service, configId),
            httpHeaders,
        )
        secureCredentialStore.putString(
            PvrCredentialKeys.basicAuthUsername(service, configId),
            basicAuthUsername,
        )
        secureCredentialStore.putString(
            PvrCredentialKeys.basicAuthPassword(service, configId),
            basicAuthPassword,
        )
    }

    override suspend fun reinheritPvrConfig(profileId: UUID, service: PvrService) {
        val profile = dao.getProfile(profileId) ?: return
        if (profile.isMain) return // main defines the base config, it has nothing to inherit from
        val configId = configIdFor(profile, service) ?: return // already inheriting
        dao.deletePvrServiceConfig(configId)
        removeSecrets(service, configId)
        setConfigIdFor(profile, service, null)
        dao.updateProfile(profile)
    }

    private fun currentProfileId(): UUID? =
        appPreferences.getValue(appPreferences.currentProfileId)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    private fun removeSecrets(service: PvrService, configId: UUID) {
        secureCredentialStore.remove(PvrCredentialKeys.apiKey(service, configId))
        secureCredentialStore.remove(PvrCredentialKeys.httpHeaders(service, configId))
        secureCredentialStore.remove(PvrCredentialKeys.basicAuthUsername(service, configId))
        secureCredentialStore.remove(PvrCredentialKeys.basicAuthPassword(service, configId))
    }

    private fun configIdFor(profile: Profile, service: PvrService): UUID? =
        when (service) {
            PvrService.SONARR -> profile.sonarrConfigId
            PvrService.RADARR -> profile.radarrConfigId
            PvrService.SEERR -> profile.seerrConfigId
        }

    private fun setConfigIdFor(profile: Profile, service: PvrService, configId: UUID?) {
        when (service) {
            PvrService.SONARR -> profile.sonarrConfigId = configId
            PvrService.RADARR -> profile.radarrConfigId = configId
            PvrService.SEERR -> profile.seerrConfigId = configId
        }
    }
}
