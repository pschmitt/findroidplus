package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository
import dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AutoDownloadRuleModule {
    @Singleton
    @Provides
    fun provideAutoDownloadRuleRepository(
        serverDatabase: ServerDatabaseDao
    ): AutoDownloadRuleRepository {
        return AutoDownloadRuleRepositoryImpl(serverDatabase)
    }
}
