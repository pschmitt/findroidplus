package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Marks a process-lifetime [CoroutineScope] - one not tied to any Android component (Activity,
 * Fragment, ViewModel). Use this instead of `viewModelScope` for work that must finish even if
 * the user navigates away from the screen that started it, e.g. enqueuing a bulk "download entire
 * show" batch: `viewModelScope` is cancelled the moment its owning screen is popped off the back
 * stack, which silently truncates whatever part of the batch hadn't been enqueued yet.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {
    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
