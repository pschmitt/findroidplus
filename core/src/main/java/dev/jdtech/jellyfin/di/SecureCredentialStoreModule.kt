package dev.jdtech.jellyfin.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.security.EncryptedPrefs
import javax.inject.Singleton

private const val ENCRYPTED_PREFS_FILE_NAME = "findroid_secure_prefs"

@Module
@InstallIn(SingletonComponent::class)
object SecureCredentialStoreModule {
    // Qualified with @EncryptedPrefs to avoid colliding with the unqualified, plaintext
    // SharedPreferences binding SharedPreferencesModule provides for AppPreferences - Hilt would
    // otherwise see two providers for the same SharedPreferences type and fail to resolve either.
    @EncryptedPrefs
    @Singleton
    @Provides
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
