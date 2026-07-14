package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepositoryImpl
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
