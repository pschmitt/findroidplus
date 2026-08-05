package dev.pschmitt.jellyfin.qrsetup

import dev.pschmitt.jellyfin.backup.BackupServer
import dev.pschmitt.jellyfin.backup.PrefValue
import kotlinx.serialization.Serializable

/**
 * Everything a QR-provisioning code carries. Deliberately not
 * [dev.pschmitt.jellyfin.backup.BackupEnvelope] reused wholesale: that type's
 * [dev.pschmitt.jellyfin.backup.BackupEnvelope.preferences] is a dump of *every*
 * `SharedPreferences` key (see `BackupManager.dumpPreferences()`), which is correct for a
 * full-device backup but way more than a QR code meant to carry only the sections the user
 * explicitly checked (Jellyfin/Sonarr/Radarr/Seerr) should leak - [plainPrefs]/[secrets] here are
 * built as an explicit whitelist per section instead, see [QrConfigManager.buildEnvelope].
 *
 * [server] carries only the *current* session's user (not every user ever logged in on that server,
 * unlike a full backup) - see [QrConfigManager.buildEnvelope].
 */
@Serializable
data class QrConfigEnvelope(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long,
    val server: BackupServer? = null,
    val plainPrefs: Map<String, PrefValue> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class QrImportSummary(
    val serverImported: Boolean,
    val sonarrImported: Boolean,
    val radarrImported: Boolean,
    val seerrImported: Boolean,
)
