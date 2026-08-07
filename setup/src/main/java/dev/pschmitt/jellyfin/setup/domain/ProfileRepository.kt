package dev.pschmitt.jellyfin.setup.domain

import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.models.Profile
import dev.pschmitt.jellyfin.models.ProfileWithUserAndServer
import java.util.UUID

interface ProfileRepository {
    suspend fun getProfiles(): List<ProfileWithUserAndServer>

    suspend fun getCurrentProfile(): ProfileWithUserAndServer?

    /** Name defaults to "<user>@<server>". PVR config starts inherited (null FKs) from main. */
    suspend fun createProfile(userId: UUID, name: String? = null): Profile

    suspend fun renameProfile(profileId: UUID, name: String)

    /**
     * Repoints this profile at a different Jellyfin login on the same server. If [profileId] is the
     * currently active profile, also updates `jellyfinApi`'s access token (mirrors
     * [dev.pschmitt.jellyfin.setup.domain.SetupRepository.setCurrentUser] - only the user/token
     * changes, the server/address stay the same). Otherwise only the DB row is touched.
     */
    suspend fun setProfileUser(profileId: UUID, userId: UUID)

    /** Flips isMain old -> new. No PVR config FK re-pointing needed (inheritance is dynamic). */
    suspend fun setMainProfile(profileId: UUID)

    /** No-op / throws if [profileId] is the main profile - transfer main first. */
    suspend fun deleteProfile(profileId: UUID)

    /** Repoints JellyfinApi to this profile's server/user and updates currentProfileId. */
    suspend fun setCurrentProfile(profileId: UUID)

    /** True if this profile's FK for [service] is null (resolves to the main profile's config). */
    suspend fun isInheriting(profileId: UUID, service: PvrService): Boolean

    /**
     * Creates (or updates, if already overriding) this profile's own
     * [PvrServiceConfig][dev.pschmitt.jellyfin.models.PvrServiceConfig] row for [service] and
     * points its FK there.
     */
    suspend fun overridePvrConfig(
        profileId: UUID,
        service: PvrService,
        enabled: Boolean,
        baseUrl: String?,
        apiKey: String?,
        httpHeaders: String?,
        basicAuthUsername: String?,
        basicAuthPassword: String?,
    )

    /**
     * Deletes this profile's own override row/secrets for [service] and sets its FK back to null.
     */
    suspend fun reinheritPvrConfig(profileId: UUID, service: PvrService)
}
