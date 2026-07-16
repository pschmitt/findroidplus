package dev.jdtech.jellyfin.security

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Qualifier

/**
 * Qualifies the [SharedPreferences] instance backed by Jetpack Security's
 * `EncryptedSharedPreferences` - distinguishes it from the plain, unqualified `SharedPreferences`
 * `SharedPreferencesModule` already provides for [dev.jdtech.jellyfin.settings.domain.AppPreferences].
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class EncryptedPrefs

/**
 * Small, generic encrypted key/value store - deliberately not Sonarr/Radarr-specific, so it can
 * be reused for other secrets later (e.g. migrating the plaintext Jellyfin access token stored in
 * `User.kt`, which is out of scope for now).
 */
class SecureCredentialStore @Inject constructor(@EncryptedPrefs private val prefs: SharedPreferences) {
    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun contains(key: String): Boolean = prefs.contains(key)
}
