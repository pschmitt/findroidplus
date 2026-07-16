package dev.jdtech.jellyfin.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.AutomaticSearchOutcome
import dev.jdtech.jellyfin.repository.RadarrSearchRepository
import dev.jdtech.jellyfin.repository.SonarrSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Waits for a Sonarr/Radarr automatic search (triggered from the Season/Episode/Movie/Calendar
 * screens via [SonarrSearchRepository.searchEpisode] / [RadarrSearchRepository.searchMovie]) to
 * actually finish, then posts a notification - the search can easily outlive the ViewModel that
 * triggered it (the user has usually moved on long before the service/Prowlarr answers), so this
 * can't just be a coroutine in that ViewModel's scope. [KEY_SOURCE] decides which service's
 * repository to poll; [KEY_TARGET_ID] is a Sonarr episode id or Radarr movie id accordingly.
 */
@HiltWorker
class AutomaticSearchWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sonarrSearchRepository: SonarrSearchRepository,
    private val radarrSearchRepository: RadarrSearchRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val source = inputData.getString(KEY_SOURCE)
            val targetId = inputData.getInt(KEY_TARGET_ID, -1)
            val commandId = inputData.getInt(KEY_COMMAND_ID, -1)
            if (targetId == -1 || commandId == -1) return@withContext Result.failure()

            val outcome =
                when (source) {
                    SOURCE_SONARR -> sonarrSearchRepository.awaitAutomaticSearchResult(targetId, commandId)
                    SOURCE_RADARR -> radarrSearchRepository.awaitAutomaticSearchResult(targetId, commandId)
                    else -> return@withContext Result.failure()
                }
            outcome
                .onSuccess { notify(it, commandId) }
                .onFailure { Timber.w(it, "Failed to check automatic search result") }

            Result.success()
        }

    private fun notify(outcome: AutomaticSearchOutcome, commandId: Int) {
        createNotificationChannel()

        val title = outcome.title
        val text =
            applicationContext.getString(
                if (outcome.succeeded) {
                    CoreR.string.automatic_search_notification_found
                } else {
                    CoreR.string.automatic_search_notification_not_found
                }
            )

        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_search)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent())
                .build()

        // commandId is unique per triggered search, so concurrent searches get their own
        // notification instead of overwriting each other.
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID_BASE + commandId, notification)
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val intent =
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(CoreR.string.automatic_search_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        applicationContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val KEY_SOURCE = "KEY_SOURCE"
        const val KEY_TARGET_ID = "KEY_TARGET_ID"
        const val KEY_COMMAND_ID = "KEY_COMMAND_ID"

        const val SOURCE_SONARR = "sonarr"
        const val SOURCE_RADARR = "radarr"

        private const val CHANNEL_ID = "automatic_search"
        private const val NOTIFICATION_ID_BASE = 279_413_000
    }
}
