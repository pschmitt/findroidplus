package dev.pschmitt.jellyfin.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.BaseApplication
import dev.pschmitt.jellyfin.BuildConfig
import dev.pschmitt.jellyfin.localcontrol.AppVersionInfo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): BaseApplication {
        return app as BaseApplication
    }

    // Backs LocalControlRouter's GET /info (jollyfin-cli version) - core can't reference
    // app/phone's own generated BuildConfig directly (wrong module, and app/tv has a separate one
    // it doesn't use local control), so this binds AppVersionInfo from it here instead.
    @Singleton
    @Provides
    fun provideAppVersionInfo(): AppVersionInfo =
        object : AppVersionInfo {
            override val versionName: String = BuildConfig.VERSION_NAME
            override val versionCode: Int = BuildConfig.VERSION_CODE
            override val gitRevision: String = BuildConfig.GIT_REVISION
        }
}
