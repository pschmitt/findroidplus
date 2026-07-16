package dev.jdtech.jellyfin

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.AutoBackupScheduler
import dev.jdtech.jellyfin.work.AutoDeleteWatchedWorker
import dev.jdtech.jellyfin.work.AutoDownloadWorker
import dev.jdtech.jellyfin.work.MpvCleanupWorker
import dev.jdtech.jellyfin.work.QueueStatusScheduler
import dev.jdtech.jellyfin.work.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import okio.Path.Companion.toOkioPath
import timber.log.Timber

@HiltAndroidApp
class BaseApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val mode =
                when (appPreferences.getValue(appPreferences.theme)) {
                    "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        if (appPreferences.getValue(appPreferences.dynamicColors)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        val workManager = WorkManager.getInstance(applicationContext)

        scheduleUserDataSync(workManager)
        scheduleMpvCleanup(workManager)
        scheduleAutoDownload(workManager)
        scheduleAutoDeleteWatched(workManager)
        AutoBackupScheduler.schedule(applicationContext, appPreferences)
        QueueStatusScheduler.schedule(applicationContext, appPreferences)
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalTime::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() }))
                add(SvgDecoder.Factory())
            }
            .diskCachePolicy(
                if (appPreferences.getValue(appPreferences.imageCache)) CachePolicy.ENABLED
                else CachePolicy.DISABLED
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(
                        appPreferences.getValue(appPreferences.imageCacheSize) * 1024L * 1024
                    )
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun scheduleUserDataSync(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

        workManager
            .enqueueUniqueWork(
                uniqueWorkName = "syncUserData",
                existingWorkPolicy = ExistingWorkPolicy.KEEP,
                request = syncWorkRequest
            )
    }

    private fun checkIntervalMinutes(): Long =
        appPreferences
            .getValue(appPreferences.autoDownloadCheckIntervalMinutes)
            .coerceIn(15, 24 * 60)
            .toLong()

    private fun scheduleAutoDownload(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<AutoDownloadWorker>(checkIntervalMinutes(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = "autoDownloadRules",
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = periodicRequest,
        )

        // Also evaluate once at startup, standing in for "after library refresh/sync" - there is
        // no dedicated library-refresh worker in this codebase to hook into.
        val startupRequest =
            OneTimeWorkRequestBuilder<AutoDownloadWorker>().setConstraints(constraints).build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = "autoDownloadRulesStartup",
            existingWorkPolicy = ExistingWorkPolicy.REPLACE,
            request = startupRequest,
        )
    }

    private fun scheduleAutoDeleteWatched(workManager: WorkManager) {
        // Only keep this job scheduled while the feature is actually on - otherwise WorkManager
        // still wakes the process every interval just to run a worker that immediately no-ops.
        if (!appPreferences.getValue(appPreferences.autoDeleteWatched)) {
            workManager.cancelUniqueWork("autoDeleteWatched")
            return
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<AutoDeleteWatchedWorker>(
                    checkIntervalMinutes(),
                    TimeUnit.MINUTES,
                )
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = "autoDeleteWatched",
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = periodicRequest,
        )
    }

    private fun scheduleMpvCleanup(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupRequest =
            OneTimeWorkRequestBuilder<MpvCleanupWorker>()
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = "mpv_cleanup",
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = cleanupRequest
        )
    }
}
