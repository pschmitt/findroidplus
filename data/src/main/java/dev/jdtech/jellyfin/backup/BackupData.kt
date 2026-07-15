package dev.jdtech.jellyfin.backup

import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Everything a backup preserves: saved servers/logins, auto-download rules, every app
 * preference, and a manifest of what was downloaded (not the files themselves - those are
 * multi-GB and trivially re-downloadable from the server; the manifest just lets restore offer
 * to re-queue them).
 */
@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val createdAt: Long,
    val servers: List<BackupServer>,
    val autoDownloadRules: List<AutoDownloadRuleDto>,
    val preferences: Map<String, PrefValue>,
    val downloadedItems: List<BackupDownloadedItem>,
)

/**
 * A `SharedPreferences` value, tagged with its concrete type so restore can call the matching
 * `SharedPreferences.Editor` putter without having to guess a type back out of plain JSON (where
 * e.g. an Int and a Long are otherwise indistinguishable once round-tripped as a bare number).
 */
@Serializable
sealed interface PrefValue {
    @Serializable data class BoolValue(val value: Boolean) : PrefValue

    @Serializable data class IntValue(val value: Int) : PrefValue

    @Serializable data class LongValue(val value: Long) : PrefValue

    @Serializable data class FloatValue(val value: Float) : PrefValue

    @Serializable data class StringValue(val value: String) : PrefValue

    @Serializable data class StringSetValue(val value: Set<String>) : PrefValue
}

@Serializable
data class BackupServer(val server: Server, val addresses: List<ServerAddress>, val users: List<User>)

@Serializable
data class BackupDownloadedItem(val serverId: String, val itemId: String, val itemKind: String)

data class RestoreSummary(
    val serversRestored: Int,
    val usersRestored: Int,
    val rulesRestored: Int,
    val downloadedItems: List<BackupDownloadedItem>,
)

object BackupDownloadedItemKind {
    const val MOVIE = "movie"
    const val EPISODE = "episode"
}

/**
 * Restoring downloads requires an active, authenticated session against the right server, which
 * may not exist yet right after restore - so the picked items are stashed as JSON in
 * [dev.jdtech.jellyfin.settings.domain.AppPreferences.pendingRestoreDownloads] and processed
 * later once a session for the matching server is active.
 */
private val pendingRestoreDownloadsJson = Json { prettyPrint = false }

fun encodePendingRestoreDownloads(items: List<BackupDownloadedItem>): String =
    pendingRestoreDownloadsJson.encodeToString(
        ListSerializer(BackupDownloadedItem.serializer()),
        items,
    )

fun decodePendingRestoreDownloads(json: String): List<BackupDownloadedItem> =
    pendingRestoreDownloadsJson.decodeFromString(
        ListSerializer(BackupDownloadedItem.serializer()),
        json,
    )
