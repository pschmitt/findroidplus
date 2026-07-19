package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.time.Instant
import java.time.ZoneId
import org.jellyfin.sdk.model.api.ItemFields
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
        onlyUnwatched: Boolean = false,
    ) {
        if (!rule.enabled) return

        val ruleCreatedAt =
            Instant.ofEpochMilli(rule.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()

        // Resolved once per evaluate() call, not per episode - the preference can't change
        // mid-loop, and this is what makes every episode land on the user's actually-configured
        // download location instead of always ending up on storage index 0 regardless of it.
        val storageIndex = downloader.resolvePreferredStorageIndex()

        val ruleSeasonId = rule.seasonId
        val seasonIds =
            try {
                if (ruleSeasonId == null) {
                    repository.getSeasons(rule.seriesId).map { it.id }
                } else {
                    listOf(ruleSeasonId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to evaluate auto-download rule ${rule.id}")
                return
            }

        for (seasonId in seasonIds) {
            val episodes =
                try {
                    repository.getEpisodes(
                        seriesId = rule.seriesId,
                        seasonId = seasonId,
                        fields = listOf(ItemFields.MEDIA_SOURCES),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch episodes for season $seasonId")
                    continue
                }

            // Each episode is enqueued independently - one episode failing to download (a
            // transient network error, missing media source, etc.) must not abort the rest of
            // the season/show batch.
            for (episode in episodes) {
                try {
                    // A sources row already exists the moment a download is enqueued (before it
                    // finishes), so its mere presence covers downloaded/queued/running alike.
                    if (database.getSources(episode.id).isNotEmpty()) continue

                    if ((onlyUnwatched || rule.onlyUnwatched) && episode.played) continue

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
                    downloader.downloadItem(episode, sourceId, storageIndex = storageIndex)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to queue download for episode ${episode.id}")
                }
            }
        }
    }
}
