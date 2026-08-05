package dev.pschmitt.jellyfin.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleEnvelope =
        BackupEnvelope(
            createdAt = 1_700_000_000_000L,
            servers = emptyList(),
            autoDownloadRules = emptyList(),
            preferences = emptyMap(),
            downloadedItems = emptyList(),
            appVersionName = "2.13.0",
            appVersionCode = 51L,
            packageId = "dev.pschmitt.jollyfin",
        )

    @Test
    fun `round-trips the new app-identity fields`() {
        val encoded = json.encodeToString(BackupEnvelope.serializer(), sampleEnvelope)
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)

        assertEquals("2.13.0", decoded.appVersionName)
        assertEquals(51L, decoded.appVersionCode)
        assertEquals("dev.pschmitt.jollyfin", decoded.packageId)
    }

    @Test
    fun `decodes a pre-existing backup that predates the app-identity fields`() {
        // What a real .frb written before this ticket looks like - no appVersionName/
        // appVersionCode/packageId keys at all.
        val oldFormatJson =
            """
            {"version":1,"createdAt":1690000000000,"servers":[],"autoDownloadRules":[],
            "preferences":{},"downloadedItems":[]}
            """
                .trimIndent()

        val decoded = json.decodeFromString(BackupEnvelope.serializer(), oldFormatJson)

        assertEquals("", decoded.appVersionName)
        assertEquals(0L, decoded.appVersionCode)
        assertEquals("", decoded.packageId)
    }

    @Test
    fun `decodes a backup carrying fields this build doesn't know about yet`() {
        // Simulates a future app version's backup adding a field this build has never heard
        // of - ignoreUnknownKeys must be set for this to not blow up.
        val futureFormatJson =
            """
            {"version":1,"createdAt":1690000000000,"servers":[],"autoDownloadRules":[],
            "preferences":{},"downloadedItems":[],"appVersionName":"3.0.0","appVersionCode":99,
            "packageId":"dev.pschmitt.jollyfin","aBrandNewFieldThisBuildHasNeverSeen":"whatever"}
            """
                .trimIndent()

        val decoded = json.decodeFromString(BackupEnvelope.serializer(), futureFormatJson)

        assertEquals("3.0.0", decoded.appVersionName)
    }

    @Test
    fun `UnsupportedBackupVersionException names the writing app version when known`() {
        val exception =
            UnsupportedBackupVersionException(backupVersion = 2, writtenByAppVersion = "3.0.0")

        assertTrue(exception.message!!.contains("3.0.0"))
        assertTrue(exception.message!!.contains("2"))
    }

    @Test
    fun `UnsupportedBackupVersionException degrades gracefully when the app version is blank`() {
        val exception =
            UnsupportedBackupVersionException(backupVersion = 2, writtenByAppVersion = "")

        assertTrue(exception.message!!.contains("newer version of the app"))
        assertTrue(exception.message!!.contains("2"))
    }
}
