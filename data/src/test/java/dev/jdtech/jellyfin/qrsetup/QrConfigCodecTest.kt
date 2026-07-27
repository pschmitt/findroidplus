package dev.jdtech.jellyfin.qrsetup

import dev.jdtech.jellyfin.backup.BackupCrypto
import dev.jdtech.jellyfin.backup.BackupServer
import dev.jdtech.jellyfin.backup.PrefValue
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.User
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrConfigCodecTest {

    private val sampleEnvelope =
        QrConfigEnvelope(
            createdAt = 1_700_000_000_000L,
            server =
                BackupServer(
                    server =
                        Server(
                            id = "server-1",
                            name = "Home Jellyfin",
                            currentServerAddressId = null,
                            currentUserId = null,
                        ),
                    addresses =
                        listOf(
                            ServerAddress(
                                id = UUID.randomUUID(),
                                serverId = "server-1",
                                address = "https://jellyfin.example.com",
                            )
                        ),
                    users =
                        listOf(
                            User(
                                id = UUID.randomUUID(),
                                name = "philipp",
                                serverId = "server-1",
                                accessToken = "s3cr3t-token",
                            )
                        ),
                ),
            plainPrefs = mapOf("pref_pvr_sonarr_enabled" to PrefValue.BoolValue(true)),
            secrets = mapOf("sonarr_api_key" to "abc123"),
        )

    @Test
    fun `round trips without a password`() {
        val payload = QrConfigCodec.encodePayload(sampleEnvelope, password = null)
        val decoded = QrConfigCodec.decodePayload(payload, password = null)
        assertEquals(sampleEnvelope, decoded)
    }

    @Test
    fun `round trips with a password`() {
        val payload = QrConfigCodec.encodePayload(sampleEnvelope, password = "hunter2")
        val decoded = QrConfigCodec.decodePayload(payload, password = "hunter2")
        assertEquals(sampleEnvelope, decoded)
    }

    @Test
    fun `decoding an encrypted payload without a password throws`() {
        val payload = QrConfigCodec.encodePayload(sampleEnvelope, password = "hunter2")
        assertThrows(BackupCrypto.PasswordRequiredException::class.java) {
            QrConfigCodec.decodePayload(payload, password = null)
        }
    }

    @Test
    fun `decoding with the wrong password throws`() {
        val payload = QrConfigCodec.encodePayload(sampleEnvelope, password = "hunter2")
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            QrConfigCodec.decodePayload(payload, password = "wrong")
        }
    }

    @Test
    fun `decoding garbage throws InvalidPayloadException`() {
        assertThrows(QrConfigCodec.InvalidPayloadException::class.java) {
            QrConfigCodec.decodePayload("not a real payload!!", password = null)
        }
    }

    @Test
    fun `decoding a newer payload version throws UnsupportedVersionException`() {
        val future = sampleEnvelope.copy(version = QrConfigEnvelope.CURRENT_VERSION + 1)
        val payload = QrConfigCodec.encodePayload(future, password = null)
        val exception =
            assertThrows(QrConfigCodec.UnsupportedVersionException::class.java) {
                QrConfigCodec.decodePayload(payload, password = null)
            }
        assertEquals(QrConfigEnvelope.CURRENT_VERSION + 1, exception.payloadVersion)
    }
}
