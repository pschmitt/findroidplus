package dev.jdtech.jellyfin.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecureCredentialStoreTest {
    private lateinit var store: SecureCredentialStore

    @Before
    fun setUp() {
        store = SecureCredentialStore(FakeSharedPreferences())
    }

    @Test
    fun `putString then getString round-trips the value`() {
        store.putString("sonarr_api_key", "abc123")

        assertEquals("abc123", store.getString("sonarr_api_key"))
    }

    @Test
    fun `getString returns null for a key that was never set`() {
        assertNull(store.getString("missing_key"))
    }

    @Test
    fun `putString with a null value removes the key`() {
        store.putString("sonarr_api_key", "abc123")

        store.putString("sonarr_api_key", null)

        assertNull(store.getString("sonarr_api_key"))
        assertFalse(store.contains("sonarr_api_key"))
    }

    @Test
    fun `remove deletes a previously stored key`() {
        store.putString("radarr_api_key", "xyz789")

        store.remove("radarr_api_key")

        assertNull(store.getString("radarr_api_key"))
        assertFalse(store.contains("radarr_api_key"))
    }

    @Test
    fun `contains reflects whether a key is currently stored`() {
        assertFalse(store.contains("sonarr_api_key"))

        store.putString("sonarr_api_key", "abc123")

        assertTrue(store.contains("sonarr_api_key"))
    }
}
