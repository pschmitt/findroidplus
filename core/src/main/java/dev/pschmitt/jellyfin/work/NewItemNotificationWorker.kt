package dev.pschmitt.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.AutoDownloadRuleDto
import dev.pschmitt.jellyfin.models.FindroidEpisode
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.FindroidMovie
import dev.pschmitt.jellyfin.models.SortBy
import dev.pschmitt.jellyfin.models.SortOrder
import dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.utils.coversEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/**
 * Periodically diffs the current Jellyfin library (movies + episodes, the whole library - not
 * just PVR-tracked items) against what was seen on the previous check, and posts a batched
 * notification via [NewItemNotifier] for anything genuinely new. Scoped to the current server
 * only, same as [AutoDownloadWorker] and for the same reason: [JellyfinRepository] is a Hilt
 * singleton bound to whichever server is "current", so diffing against any other server here
 * would compare the wrong library.
 *
 * State (a last-checked timestamp, and the bounded set of item ids seen on the last check - used
 * to detect "new since last check") is kept in [AppPreferences] as plain preference values rather
 * than a new Room table/column - deliberately, to avoid bumping the DB schema version while a
 * separate feature (pending pre-order downloads) is being built concurrently in another worktree
 * that also touches Room. The seen-ids set is naturally self-bounding: each cycle simply replaces
 * it with the ids from that cycle's fetch (capped at [FETCH_LIMIT]), so it never grows without
 * bound - at the cost of an item being able to scroll out of the seen-window unobserved if more
 * than [FETCH_LIMIT] items land in the library between two checks, a tradeoff against persisting
 * an ever-growing id list.
 */
@HiltWorker
class NewItemNotificationWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val ruleRepository: AutoDownloadRuleRepository,
    private val notifier: NewItemNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            if (!appPreferences.getValue(appPreferences.newItemNotificationsEnabled)) {
                return@withContext Result.success()
            }

            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext Result.success()
            val userId = jellyfinRepository.getUserId()

            val fetched =
                try {
                    jellyfinRepository.getItems(
                        includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        recursive = true,
                        sortBy = SortBy.DATE_ADDED,
                        sortOrder = SortOrder.DESCENDING,
                        limit = FETCH_LIMIT,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch items for new-item notification check")
                    return@withContext Result.retry()
                }

            val fetchedIds = fetched.map { it.id.toString() }.toSet()
            val previouslySeenIds = readSeenIds()
            val isFirstCheck =
                previouslySeenIds.isEmpty() &&
                    appPreferences.getValue(appPreferences.newItemNotificationsLastCheckMillis) == 0L

            // First-ever check: nothing has been "seen" yet, so every item currently in the
            // library would otherwise look "new". Just record the baseline instead of firing a
            // notification for the entire pre-existing catalog the moment this feature is turned
            // on.
            if (!isFirstCheck) {
                val newItems = fetched.filter { it.id.toString() !in previouslySeenIds }
                if (newItems.isNotEmpty()) {
                    val enabledRules = ruleRepository.getEnabledRules(serverId, userId)
                    notifier.notifyNewItems(newItems.map { toNotifyItem(it, enabledRules) })
                }
            }

            appPreferences.setValue(
                appPreferences.newItemNotificationsSeenItemIds,
                fetchedIds.joinToString(","),
            )
            appPreferences.setValue(
                appPreferences.newItemNotificationsLastCheckMillis,
                System.currentTimeMillis(),
            )

            Result.success()
        }
    }

    private fun readSeenIds(): Set<String> =
        appPreferences
            .getValue(appPreferences.newItemNotificationsSeenItemIds)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun toNotifyItem(
        item: FindroidItem,
        enabledRules: List<AutoDownloadRuleDto>,
    ): NewItemNotifier.NewItem {
        // A sources row already exists the moment a download is enqueued (before it finishes),
        // so its mere presence covers downloaded/queued/running alike - same dedup check
        // AutoDownloadRuleEvaluator uses.
        val alreadyHasSource = database.getSources(item.id).isNotEmpty()
        val downloadEligible =
            item.canDownload &&
                !alreadyHasSource &&
                when (item) {
                    // Auto-download rules are series/season scoped only - movies are never
                    // covered by one, so every downloadable new movie is eligible.
                    is FindroidMovie -> true
                    is FindroidEpisode -> enabledRules.none { coversEpisode(it, item) }
                    else -> false
                }

        val title =
            when (item) {
                is FindroidEpisode ->
                    "${item.seriesName} · S${item.parentIndexNumber}E${item.indexNumber} · ${item.name}"
                else -> item.name
            }

        return NewItemNotifier.NewItem(
            id = item.id,
            title = title,
            isMovie = item is FindroidMovie,
            downloadEligible = downloadEligible,
        )
    }

    private companion object {
        // Bounds both the size of the persisted "seen ids" preference and the notification batch
        // this worker will ever try to build in one cycle.
        const val FETCH_LIMIT = 100
    }
}
