package dev.jdtech.jellyfin.backup

import android.content.Context
import android.net.Uri
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Builds, encodes/decodes, and restores backups. Not `@Inject`-constructed directly (`data`
 * module has no Hilt setup, matching how `JellyfinRepositoryImpl` etc. are wired) - see
 * core/di/BackupModule.kt for the Hilt `@Provides` binding.
 */
class BackupManager(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) {
    private val json = Json { prettyPrint = false }

    suspend fun buildBackup(): BackupEnvelope =
        withContext(Dispatchers.IO) {
            val servers =
                database.getAllServersWithAddressesAndUsers().map {
                    BackupServer(server = it.server, addresses = it.addresses, users = it.users)
                }
            BackupEnvelope(
                createdAt = System.currentTimeMillis(),
                servers = servers,
                autoDownloadRules = database.getAllAutoDownloadRules(),
                preferences = dumpPreferences(),
                downloadedItems = buildDownloadedItemsManifest(),
            )
        }

    suspend fun writeBackup(envelope: BackupEnvelope, uri: Uri, password: String?) {
        withContext(Dispatchers.IO) {
            val plainBytes = json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray()
            val bytes = BackupCrypto.encode(plainBytes, password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open $uri for writing")
        }
    }

    /** @throws BackupCrypto.PasswordRequiredException, BackupCrypto.WrongPasswordException */
    suspend fun readBackup(uri: Uri, password: String?): BackupEnvelope =
        withContext(Dispatchers.IO) {
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not open $uri for reading")
            val plainBytes = BackupCrypto.decode(bytes, password)
            json.decodeFromString(BackupEnvelope.serializer(), String(plainBytes))
        }

    fun isBackupEncrypted(uri: Uri): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        return BackupCrypto.isEncrypted(bytes)
    }

    suspend fun restore(envelope: BackupEnvelope): RestoreSummary =
        withContext(Dispatchers.IO) {
            for (backupServer in envelope.servers) {
                database.insertServer(backupServer.server)
                for (address in backupServer.addresses) database.insertServerAddress(address)
                for (user in backupServer.users) database.insertUser(user)
            }
            for (rule in envelope.autoDownloadRules) database.insertAutoDownloadRule(rule)
            restorePreferences(envelope.preferences)

            RestoreSummary(
                serversRestored = envelope.servers.size,
                usersRestored = envelope.servers.sumOf { it.users.size },
                rulesRestored = envelope.autoDownloadRules.size,
                downloadedItems = envelope.downloadedItems,
            )
        }

    private fun buildDownloadedItemsManifest(): List<BackupDownloadedItem> {
        val items = mutableListOf<BackupDownloadedItem>()
        for (server in database.getAllServersSync()) {
            for (movie in database.getMoviesByServerId(server.id)) {
                if (database.getSources(movie.id).any { it.type == FindroidSourceType.LOCAL }) {
                    items.add(
                        BackupDownloadedItem(
                            serverId = server.id,
                            itemId = movie.id.toString(),
                            itemKind = BackupDownloadedItemKind.MOVIE,
                        )
                    )
                }
            }
            for (episode in database.getEpisodesByServerId(server.id)) {
                if (database.getSources(episode.id).any { it.type == FindroidSourceType.LOCAL }) {
                    items.add(
                        BackupDownloadedItem(
                            serverId = server.id,
                            itemId = episode.id.toString(),
                            itemKind = BackupDownloadedItemKind.EPISODE,
                        )
                    )
                }
            }
        }
        return items
    }

    private fun dumpPreferences(): Map<String, PrefValue> {
        val result = mutableMapOf<String, PrefValue>()
        for ((key, value) in appPreferences.sharedPreferences.all) {
            // Ephemeral in-flight state, not a user setting - must never round-trip through a
            // backup. If it were captured here, restoring an old backup could resurrect a stale
            // pending-download signal that overrides whatever the user answers in the restore
            // flow's own "redownload?" prompt (see RestoreBackupViewModel.OnRedownloadNo).
            if (key == appPreferences.pendingRestoreDownloads.backendName) continue
            result[key] =
                when (value) {
                    is Boolean -> PrefValue.BoolValue(value)
                    is Int -> PrefValue.IntValue(value)
                    is Long -> PrefValue.LongValue(value)
                    is Float -> PrefValue.FloatValue(value)
                    is String -> PrefValue.StringValue(value)
                    is Set<*> ->
                        PrefValue.StringSetValue(value.filterIsInstance<String>().toSet())
                    else -> continue
                }
        }
        return result
    }

    private fun restorePreferences(preferences: Map<String, PrefValue>) {
        val editor = appPreferences.sharedPreferences.edit()
        for ((key, value) in preferences) {
            when (value) {
                is PrefValue.BoolValue -> editor.putBoolean(key, value.value)
                is PrefValue.IntValue -> editor.putInt(key, value.value)
                is PrefValue.LongValue -> editor.putLong(key, value.value)
                is PrefValue.FloatValue -> editor.putFloat(key, value.value)
                is PrefValue.StringValue -> editor.putString(key, value.value)
                is PrefValue.StringSetValue -> editor.putStringSet(key, value.value)
            }
        }
        // commit() rather than apply() - the caller restarts the whole process right after a
        // successful restore (to rebuild JellyfinApi and other @Singleton state from the
        // now-current server/user), which would otherwise race apply()'s async disk write and
        // could lose the just-restored preferences on the very next cold start.
        editor.commit()
    }
}
