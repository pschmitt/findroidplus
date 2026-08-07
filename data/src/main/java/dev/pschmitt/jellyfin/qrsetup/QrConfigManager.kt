package dev.pschmitt.jellyfin.qrsetup

import dev.pschmitt.jellyfin.api.pvr.PvrClientConfigFull
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.backup.BackupServer
import dev.pschmitt.jellyfin.backup.PrefValue
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.ServerWithAddressesAndUsers
import dev.pschmitt.jellyfin.models.User
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.settings.domain.models.Preference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Overrides the Jellyfin user embedded in the payload with a freshly-authenticated one, instead of
 * reusing whichever [dev.pschmitt.jellyfin.models.User] row is already in Room - see
 * `QrExportViewModel`'s "different login" flow, which performs the actual authentication.
 */
data class JellyfinUserOverride(val userId: UUID, val userName: String, val accessToken: String)

/**
 * Overrides a PVR service's base URL/API key for this export only - not persisted back to
 * `AppPreferences`/`SecureCredentialStore`.
 */
data class PvrOverride(val baseUrl: String, val apiKey: String)

/**
 * Builds and applies QR-provisioning payloads (FINDROID-43) - the DB/prefs-touching half; see
 * [QrConfigCodec] for the pure encode/decode half. Not `@Inject`-constructed directly (`data`
 * module has no Hilt setup) - see `core/di/QrConfigModule.kt` for the Hilt `@Provides` binding,
 * same pattern as [dev.pschmitt.jellyfin.backup.BackupManager].
 *
 * [resolvePvrConfig] resolves the selected profile's effective Sonarr/Radarr/Seerr config from
 * `core`'s `PvrConfigResolver`, and [putSecret] writes `SecureCredentialStore` - both plain
 * lambdas, since those types live in `core`, which depends on `data`, not the other way around.
 */
class QrConfigManager(
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val resolvePvrConfig: (UUID?, PvrService) -> PvrClientConfigFull? = { _, _ -> null },
    private val putSecret: (key: String, value: String) -> Unit = { _, _ -> },
    // See BackupManager's identical parameter for why this is needed.
    private val reconcileProfiles: () -> Unit = {},
) {
    suspend fun buildEnvelope(
        profileId: UUID? = null,
        includeJellyfin: Boolean,
        jellyfinServerId: String?,
        jellyfinUserId: UUID?,
        jellyfinOverride: JellyfinUserOverride? = null,
        includeSonarr: Boolean,
        sonarrOverride: PvrOverride? = null,
        includeRadarr: Boolean,
        radarrOverride: PvrOverride? = null,
        includeSeerr: Boolean,
        seerrOverride: PvrOverride? = null,
    ): QrConfigEnvelope =
        withContext(Dispatchers.IO) {
            val plainPrefs = mutableMapOf<String, PrefValue>()
            val secrets = mutableMapOf<String, String>()

            if (includeSonarr) {
                putPvrFields(
                    plainPrefs,
                    secrets,
                    appPreferences.sonarrBaseUrl,
                    PvrService.SONARR,
                    profileId,
                    sonarrOverride,
                )
            }
            if (includeRadarr) {
                putPvrFields(
                    plainPrefs,
                    secrets,
                    appPreferences.radarrBaseUrl,
                    PvrService.RADARR,
                    profileId,
                    radarrOverride,
                )
            }
            if (includeSeerr) {
                putPvrFields(
                    plainPrefs,
                    secrets,
                    appPreferences.seerrBaseUrl,
                    PvrService.SEERR,
                    profileId,
                    seerrOverride,
                )
            }

            QrConfigEnvelope(
                createdAt = System.currentTimeMillis(),
                server =
                    if (includeJellyfin && jellyfinServerId != null) {
                        buildServer(jellyfinServerId, jellyfinUserId, jellyfinOverride)
                    } else {
                        null
                    },
                plainPrefs = plainPrefs,
                secrets = secrets,
            )
        }

    /** Every locally-known server (with its addresses/users), for the export screen's picker. */
    suspend fun getAvailableServers(): List<ServerWithAddressesAndUsers> =
        withContext(Dispatchers.IO) { database.getAllServersWithAddressesAndUsers() }

    /**
     * Currently-stored base URL for [profileId], to pre-fill the export screen's editable field.
     * The API key is deliberately not exposed here - like `jellyfinPassword`, its export-screen
     * field starts blank and only the export's [putPvrFields] falls back to the stored secret. Empty
     * string if not configured.
     */
    fun currentSonarrBaseUrl(profileId: UUID? = null): String =
        resolvePvrConfig(profileId, PvrService.SONARR)?.baseUrl.orEmpty()

    fun currentRadarrBaseUrl(profileId: UUID? = null): String =
        resolvePvrConfig(profileId, PvrService.RADARR)?.baseUrl.orEmpty()

    fun currentSeerrBaseUrl(profileId: UUID? = null): String =
        resolvePvrConfig(profileId, PvrService.SEERR)?.baseUrl.orEmpty()

    suspend fun applyEnvelope(envelope: QrConfigEnvelope): QrImportSummary =
        withContext(Dispatchers.IO) {
            envelope.server?.let { applyServer(it) }

            for ((key, value) in envelope.plainPrefs) {
                val editor = appPreferences.sharedPreferences.edit()
                when (value) {
                    is PrefValue.BoolValue -> editor.putBoolean(key, value.value)
                    is PrefValue.IntValue -> editor.putInt(key, value.value)
                    is PrefValue.LongValue -> editor.putLong(key, value.value)
                    is PrefValue.FloatValue -> editor.putFloat(key, value.value)
                    is PrefValue.StringValue -> editor.putString(key, value.value)
                    is PrefValue.StringSetValue -> editor.putStringSet(key, value.value)
                }
                editor.apply()
            }
            // Writes into the legacy flat SecureCredentialStore keys buildEnvelope()/putPvrFields()
            // labels its export with (see PvrCredentialKeys.legacyApiKey() and friends) -
            // reconcileProfiles() below reads these same legacy keys (plus the plainPrefs restore
            // above) and folds them into the main profile's real PvrServiceConfig + namespaced
            // secrets.
            for ((key, value) in envelope.secrets) putSecret(key, value)
            reconcileProfiles()

            QrImportSummary(
                serverImported = envelope.server != null,
                sonarrImported =
                    envelope.plainPrefs.containsKey(appPreferences.sonarrEnabled.backendName),
                radarrImported =
                    envelope.plainPrefs.containsKey(appPreferences.radarrEnabled.backendName),
                seerrImported =
                    envelope.plainPrefs.containsKey(appPreferences.seerrEnabled.backendName),
            )
        }

    private fun buildServer(
        serverId: String,
        userId: UUID?,
        override: JellyfinUserOverride?,
    ): BackupServer? {
        val serverData = database.getServerWithAddressesAndUsers(serverId) ?: return null
        val user =
            if (override != null) {
                User(
                    id = override.userId,
                    name = override.userName,
                    serverId = serverId,
                    accessToken = override.accessToken,
                )
            } else {
                serverData.users.find { it.id == userId }
                    ?: serverData.users.find { it.id == serverData.server.currentUserId }
                    ?: return null
            }
        return BackupServer(
            server = serverData.server,
            addresses = serverData.addresses,
            users = listOf(user),
        )
    }

    private fun applyServer(backupServer: BackupServer) {
        database.insertServer(backupServer.server)
        for (address in backupServer.addresses) database.insertServerAddress(address)
        for (user in backupServer.users) database.insertUser(user)
        appPreferences.setValue(appPreferences.currentServer, backupServer.server.id)
    }

    /**
     * Reads the active profile's resolved Sonarr/Radarr/Seerr config - MUST go through
     * [resolvePvrConfig] (the profile-aware [dev.pschmitt.jellyfin.pvr.PvrConfigResolver]), not a
     * direct `SecureCredentialStore` read keyed by [PvrCredentialKeys]'s legacy flat constants,
     * since those are stale/unused once a profile has its own namespaced override. Still labels
     * each exported secret with its legacy flat key name (via [PvrCredentialKeys.legacyApiKey] and
     * friends) purely as the envelope's map key - [applyEnvelope] writes it straight back with that
     * same label.
     */
    private fun putPvrFields(
        plainPrefs: MutableMap<String, PrefValue>,
        secrets: MutableMap<String, String>,
        baseUrl: Preference<String?>,
        service: PvrService,
        profileId: UUID?,
        override: PvrOverride?,
    ) {
        val config = resolvePvrConfig(profileId, service)
        plainPrefs[enabledPreference(service).backendName] = PrefValue.BoolValue(true)
        if (override != null) {
            plainPrefs[baseUrl.backendName] = PrefValue.StringValue(override.baseUrl)
            // A blank apiKey means "keep the existing one" (see QrExportState's apiKey fields),
            // same as a blank jellyfinPassword - fall back to the active profile's resolved key
            // instead of dropping the secret from the export.
            val apiKey = override.apiKey.ifBlank { config?.apiKey }
            apiKey?.let { secrets[PvrCredentialKeys.legacyApiKey(service)] = it }
        } else {
            config?.baseUrl?.let { plainPrefs[baseUrl.backendName] = PrefValue.StringValue(it) }
            config?.apiKey?.let { secrets[PvrCredentialKeys.legacyApiKey(service)] = it }
        }
        // Headers/basic-auth aren't exposed as editable override fields either way - always carry
        // over whatever's resolved for the active profile.
        config?.httpHeaders?.let { secrets[PvrCredentialKeys.legacyHttpHeaders(service)] = it }
        config?.basicAuthUsername?.let {
            secrets[PvrCredentialKeys.legacyBasicAuthUsername(service)] = it
        }
        config?.basicAuthPassword?.let {
            secrets[PvrCredentialKeys.legacyBasicAuthPassword(service)] = it
        }
    }

    private fun enabledPreference(service: PvrService): Preference<Boolean> =
        when (service) {
            PvrService.SONARR -> appPreferences.sonarrEnabled
            PvrService.RADARR -> appPreferences.radarrEnabled
            PvrService.SEERR -> appPreferences.seerrEnabled
        }
}
