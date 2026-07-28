package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.PendingDownloadRequestRepository
import dev.pschmitt.jellyfin.repository.PendingDownloadRequestRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PendingDownloadRequestModule {
    @Singleton
    @Provides
    fun providePendingDownloadRequestRepository(
        serverDatabase: ServerDatabaseDao
    ): PendingDownloadRequestRepository {
        return PendingDownloadRequestRepositoryImpl(serverDatabase)
    }
}
