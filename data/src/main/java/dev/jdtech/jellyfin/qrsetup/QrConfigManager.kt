package dev.jdtech.jellyfin.qrsetup

import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.backup.BackupServer
import dev.jdtech.jellyfin.backup.PrefValue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.Preference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Overrides the Jellyfin user embedded in the payload with a freshly-authenticated one, instead of
 * reusing whichever [dev.jdtech.jellyfin.models.User] row is already in Room - see
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
 * same pattern as [dev.jdtech.jellyfin.backup.BackupManager].
 *
 * [getSecret]/[putSecret] read/write `SecureCredentialStore` via plain lambdas, since that type
 * lives in `core`, which depends on `data`, not the other way around.
 */
class QrConfigManager(
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val getSecret: (key: String) -> String? = { null },
    private val putSecret: (key: String, value: String) -> Unit = { _, _ -> },
) {
    suspend fun buildEnvelope(
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
                    appPreferences.sonarrEnabled,
                    appPreferences.sonarrBaseUrl,
                    SONARR_SECRET_KEYS,
                    sonarrOverride,
                )
            }
            if (includeRadarr) {
                putPvrFields(
                    plainPrefs,
                    secrets,
                    appPreferences.radarrEnabled,
                    appPreferences.radarrBaseUrl,
                    RADARR_SECRET_KEYS,
                    radarrOverride,
                )
            }
            if (includeSeerr) {
                putPvrFields(
                    plainPrefs,
                    secrets,
                    appPreferences.seerrEnabled,
                    appPreferences.seerrBaseUrl,
                    SEERR_SECRET_KEYS,
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
     * Currently-stored (not overridden) base URL/API key, to pre-fill the export screen's editable
     * fields. Empty strings if not configured.
     */
    fun currentSonarrFields(): PvrOverride =
        currentPvrFields(appPreferences.sonarrBaseUrl, PvrCredentialKeys.SONARR_API_KEY)

    fun currentRadarrFields(): PvrOverride =
        currentPvrFields(appPreferences.radarrBaseUrl, PvrCredentialKeys.RADARR_API_KEY)

    fun currentSeerrFields(): PvrOverride =
        currentPvrFields(appPreferences.seerrBaseUrl, PvrCredentialKeys.SEERR_API_KEY)

    private fun currentPvrFields(baseUrl: Preference<String?>, apiKeyKey: String): PvrOverride =
        PvrOverride(
            baseUrl = appPreferences.getValue(baseUrl).orEmpty(),
            apiKey = getSecret(apiKeyKey).orEmpty(),
        )

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
            for ((key, value) in envelope.secrets) putSecret(key, value)

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

    private fun putPvrFields(
        plainPrefs: MutableMap<String, PrefValue>,
        secrets: MutableMap<String, String>,
        enabled: Preference<Boolean>,
        baseUrl: Preference<String?>,
        secretKeys: List<String>,
        override: PvrOverride?,
    ) {
        plainPrefs[enabled.backendName] = PrefValue.BoolValue(true)
        if (override != null) {
            plainPrefs[baseUrl.backendName] = PrefValue.StringValue(override.baseUrl)
            if (override.apiKey.isNotBlank()) secrets[secretKeys.first()] = override.apiKey
            // Headers/basic-auth aren't exposed as editable override fields - carry over
            // whatever's already stored for them.
            for (key in secretKeys.drop(1)) getSecret(key)?.let { secrets[key] = it }
        } else {
            appPreferences.getValue(baseUrl)?.let {
                plainPrefs[baseUrl.backendName] = PrefValue.StringValue(it)
            }
            for (key in secretKeys) getSecret(key)?.let { secrets[key] = it }
        }
    }

    private companion object {
        val SONARR_SECRET_KEYS =
            listOf(
                PvrCredentialKeys.SONARR_API_KEY,
                PvrCredentialKeys.SONARR_HTTP_HEADERS,
                PvrCredentialKeys.SONARR_BASIC_AUTH_USERNAME,
                PvrCredentialKeys.SONARR_BASIC_AUTH_PASSWORD,
            )
        val RADARR_SECRET_KEYS =
            listOf(
                PvrCredentialKeys.RADARR_API_KEY,
                PvrCredentialKeys.RADARR_HTTP_HEADERS,
                PvrCredentialKeys.RADARR_BASIC_AUTH_USERNAME,
                PvrCredentialKeys.RADARR_BASIC_AUTH_PASSWORD,
            )
        val SEERR_SECRET_KEYS =
            listOf(
                PvrCredentialKeys.SEERR_API_KEY,
                PvrCredentialKeys.SEERR_HTTP_HEADERS,
                PvrCredentialKeys.SEERR_BASIC_AUTH_USERNAME,
                PvrCredentialKeys.SEERR_BASIC_AUTH_PASSWORD,
            )
    }
}
