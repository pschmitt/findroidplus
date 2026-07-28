package dev.jdtech.jellyfin.localcontrol

import dev.jdtech.jellyfin.security.FakeSharedPreferences
import dev.jdtech.jellyfin.security.SecureCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `beginPairing records the request with the caller's real uid and package`() {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")

        assertEquals("client-1", request.clientId)
        assertEquals(10123, request.uid)
        assertEquals("com.termux", request.packageName)
        assertEquals(request, auth.pendingRequest(request.requestId))
    }

    @Test
    fun `approve issues a token that verifies for the same uid`() = runBlocking {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")

        val token = auth.approve(request.requestId)

        assertNotNull(token)
        assertTrue(auth.verifyToken(token, uid = 10123))
    }

    @Test
    fun `approve consumes the pending request`() = runBlocking {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")

        auth.approve(request.requestId)

        assertNull(auth.pendingRequest(request.requestId))
        assertNull(auth.approve(request.requestId))
    }

    @Test
    fun `verifyToken rejects a token replayed from a different uid`() = runBlocking {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")
        val token = auth.approve(request.requestId)

        assertFalse(auth.verifyToken(token, uid = 99999))
    }

    @Test
    fun `verifyToken rejects an unknown token`() = runBlocking {
        assertFalse(auth.verifyToken("not-a-real-token", uid = 10123))
    }

    @Test
    fun `verifyToken rejects a null or blank token`() = runBlocking {
        assertFalse(auth.verifyToken(null, uid = 10123))
        assertFalse(auth.verifyToken("", uid = 10123))
    }

    @Test
    fun `deny clears the pending request without issuing a token`() = runBlocking {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")

        auth.deny(request.requestId)

        assertNull(auth.pendingRequest(request.requestId))
        assertNull(auth.approve(request.requestId))
    }

    @Test
    fun `re-pairing the same client replaces its previous token`() = runBlocking {
        val first = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")
        val firstToken = auth.approve(first.requestId)

        val second = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")
        val secondToken = auth.approve(second.requestId)

        assertFalse(auth.verifyToken(firstToken, uid = 10123))
        assertTrue(auth.verifyToken(secondToken, uid = 10123))
        assertEquals(1, auth.listPairedClients().size)
    }

    @Test
    fun `revoke removes a paired client's token`() = runBlocking {
        val request = auth.beginPairing("client-1", uid = 10123, packageName = "com.termux")
        val token = auth.approve(request.requestId)

        auth.revoke("client-1")

        assertFalse(auth.verifyToken(token, uid = 10123))
        assertTrue(auth.listPairedClients().isEmpty())
    }
}
