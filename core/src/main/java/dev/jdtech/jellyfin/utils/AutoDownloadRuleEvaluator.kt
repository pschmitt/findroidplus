package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.time.Instant
import java.time.ZoneId
import timber.log.Timber

/**
 * Queues every currently-missing episode matched by [rule]. Reused by both the immediate
 * on-enable UI action and [dev.jdtech.jellyfin.work.AutoDownloadWorker] so dedup logic only
 * lives in one place.
 */
class AutoDownloadRuleEvaluator {
    suspend fun evaluate(
        rule: AutoDownloadRuleDto,
        database: ServerDatabaseDao,
        repository: JellyfinRepository,
        downloader: Downloader,
    ) {
        if (!rule.enabled) return

        val ruleCreatedAt =
            Instant.ofEpochMilli(rule.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()

        try {
            val ruleSeasonId = rule.seasonId
            val seasonIds =
                if (ruleSeasonId == null) {
                    repository.getSeasons(rule.seriesId).map { it.id }
                } else {
                    listOf(ruleSeasonId)
                }

            for (seasonId in seasonIds) {
                val episodes =
                    try {
                        repository.getEpisodes(seriesId = rule.seriesId, seasonId = seasonId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch episodes for season $seasonId")
                        continue
                    }

                for (episode in episodes) {
                    // A sources row already exists the moment a download is enqueued (before it
                    // finishes), so its mere presence covers downloaded/queued/running alike.
                    if (database.getSources(episode.id).isNotEmpty()) continue

                    // In onlyNewEpisodes mode, never backfill the existing catalog - only queue
                    // episodes that premiered after the rule was created. An unknown premiere
                    // date is treated as new rather than silently dropped.
                    val premiereDate = episode.premiereDate
                    if (
                        rule.onlyNewEpisodes &&
                            premiereDate != null &&
                            premiereDate.isBefore(ruleCreatedAt)
                    ) {
                        continue
                    }

                    val sourceId = episode.sources.firstOrNull()?.id ?: continue
                    downloader.downloadItem(episode, sourceId, storageIndex = 0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to evaluate auto-download rule ${rule.id}")
        }
    }
}
