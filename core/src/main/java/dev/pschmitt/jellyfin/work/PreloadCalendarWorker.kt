package dev.pschmitt.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.jellyfin.repository.CalendarCache
import dev.pschmitt.jellyfin.repository.CalendarRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Warms [CalendarCache] in the background so the Calendar tab and a show's "next airing" already
 * have data by the time the user looks at either, instead of both cold-starting a Sonarr/Radarr/
 * Jellyfin fetch on first access. Skips the fetch entirely when the cache is still within its 12h
 * TTL ([CalendarCache.isFresh]) - scheduled both periodically (in case the app process lives past
 * that window without a restart) and once at startup, same two-job shape as
 * `AutoDownloadWorker`/`PendingDownloadWorker`.
 */
@HiltWorker
class PreloadCalendarWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendarRepository: CalendarRepository,
    private val calendarCache: CalendarCache,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            appPreferences.getValue(appPreferences.currentServer)
                ?: return@withContext Result.success()
            if (calendarCache.isFresh()) return@withContext Result.success()

            try {
                calendarCache.update(calendarRepository.getUpcoming())
            } catch (e: Exception) {
                Timber.w(e, "Failed to preload calendar cache")
            }

            Result.success()
        }
}
