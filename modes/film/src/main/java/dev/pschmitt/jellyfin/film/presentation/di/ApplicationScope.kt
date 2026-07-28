package dev.pschmitt.jellyfin.film.presentation.di

import javax.inject.Qualifier

/** A [kotlinx.coroutines.CoroutineScope] that outlives any single ViewModel. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
