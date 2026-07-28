package dev.pschmitt.jellyfin.localcontrol

import dev.pschmitt.jellyfin.security.FakeSharedPreferences
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalControlAuthTest {
    private lateinit var auth: LocalControlAuth

    @Before
    fun setUp() {
        auth = LocalControlAuth(SecureCredentialStore(FakeSharedPreferences()))
    }

    @Test
    fun `getOrCreateToken generates a token on first call`() {
        val token = auth.getOrCreateToken()

        assertTrue(token.isNotBlank())
    }

    @Test
    fun `getOrCreateToken returns the same token on repeated calls`() {
        val first = auth.getOrCreateToken()
        val second = auth.getOrCreateToken()

        assertEquals(first, second)
    }

    @Test
    fun `verifyToken accepts the current token`() {
        val token = auth.getOrCreateToken()

        assertTrue(auth.verifyToken(token))
    }

    @Test
    fun `verifyToken rejects a wrong token`() {
        auth.getOrCreateToken()

        assertFalse(auth.verifyToken("not-the-real-token"))
    }

    @Test
    fun `verifyToken rejects null or blank`() {
        auth.getOrCreateToken()

        assertFalse(auth.verifyToken(null))
        assertFalse(auth.verifyToken(""))
    }

    @Test
    fun `verifyToken rejects an unset token`() {
        assertFalse(auth.verifyToken("anything"))
    }

    @Test
    fun `regenerateToken issues a different token and invalidates the old one`() {
        val original = auth.getOrCreateToken()

        val regenerated = auth.regenerateToken()

        assertNotEquals(original, regenerated)
        assertFalse(auth.verifyToken(original))
        assertTrue(auth.verifyToken(regenerated))
    }
}
