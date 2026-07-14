package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.AutoDownloadRuleEvaluator
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Evaluates auto-download rules for the currently active server/user only. [Downloader] and
 * [JellyfinRepository] are both Hilt singletons scoped to the "current" server (see
 * DownloaderImpl's use of appPreferences.currentServer when tagging downloaded rows), so
 * evaluating any other server here would mis-tag persisted rows. Rules for inactive servers stay
 * correctly persisted and get evaluated whenever the user switches to that server.
 */
@HiltWorker
class AutoDownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
    private val ruleRepository: AutoDownloadRuleRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer) ?: return@withContext Result.success()
            val userId = jellyfinRepository.getUserId()

            val evaluator = AutoDownloadRuleEvaluator()
            for (rule in ruleRepository.getEnabledRules(serverId, userId)) {
                evaluator.evaluate(rule, database, jellyfinRepository, downloader)
            }

            Result.success()
        }
    }
}
