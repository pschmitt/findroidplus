package dev.jdtech.jellyfin.qrsetup

import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.backup.BackupServer
import dev.jdtech.jellyfin.backup.PrefValue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.Preference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        includeSonarr: Boolean,
        includeRadarr: Boolean,
        includeSeerr: Boolean,
    ): QrConfigEnvelope =
        withContext(Dispatchers.IO) {
            val plainPrefs = mutableMapOf<String, PrefValue>()
            val secrets = mutableMapOf<String, String>()

            if (includeSonarr) {
                putIfEnabled(plainPrefs, appPreferences.sonarrEnabled, appPreferences.sonarrBaseUrl)
                putSecrets(secrets, SONARR_SECRET_KEYS)
            }
            if (includeRadarr) {
                putIfEnabled(plainPrefs, appPreferences.radarrEnabled, appPreferences.radarrBaseUrl)
                putSecrets(secrets, RADARR_SECRET_KEYS)
            }
            if (includeSeerr) {
                putIfEnabled(plainPrefs, appPreferences.seerrEnabled, appPreferences.seerrBaseUrl)
                putSecrets(secrets, SEERR_SECRET_KEYS)
            }

            QrConfigEnvelope(
                createdAt = System.currentTimeMillis(),
                server =
                    if (includeJellyfin && jellyfinServerId != null) {
                        buildServer(jellyfinServerId, jellyfinUserId)
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

    private fun buildServer(serverId: String, userId: UUID?): BackupServer? {
        val serverData = database.getServerWithAddressesAndUsers(serverId) ?: return null
        val user =
            serverData.users.find { it.id == userId }
                ?: serverData.users.find { it.id == serverData.server.currentUserId }
        return BackupServer(
            server = serverData.server,
            addresses = serverData.addresses,
            users = listOfNotNull(user),
        )
    }

    private fun applyServer(backupServer: BackupServer) {
        database.insertServer(backupServer.server)
        for (address in backupServer.addresses) database.insertServerAddress(address)
        for (user in backupServer.users) database.insertUser(user)
        appPreferences.setValue(appPreferences.currentServer, backupServer.server.id)
    }

    private fun putIfEnabled(
        prefs: MutableMap<String, PrefValue>,
        enabled: Preference<Boolean>,
        baseUrl: Preference<String?>,
    ) {
        prefs[enabled.backendName] = PrefValue.BoolValue(appPreferences.getValue(enabled))
        appPreferences.getValue(baseUrl)?.let {
            prefs[baseUrl.backendName] = PrefValue.StringValue(it)
        }
    }

    private fun putSecrets(secrets: MutableMap<String, String>, keys: List<String>) {
        for (key in keys) getSecret(key)?.let { secrets[key] = it }
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
