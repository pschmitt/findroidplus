package dev.pschmitt.jellyfin.utils

import dev.pschmitt.jellyfin.core.Constants
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.AutoDownloadRuleDto
import dev.pschmitt.jellyfin.models.FindroidEpisode
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import java.time.Instant
import java.time.ZoneId
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

/**
 * Queues every currently-missing episode matched by [rule]. Reused by both the immediate
 * on-enable UI action and [dev.pschmitt.jellyfin.work.AutoDownloadWorker] so dedup logic only
 * lives in one place.
 */
class AutoDownloadRuleEvaluator {
    suspend fun evaluate(
        rule: AutoDownloadRuleDto,
        database: ServerDatabaseDao,
        repository: JellyfinRepository,
        downloader: Downloader,
        appPreferences: AppPreferences,
        onlyUnwatched: Boolean = false,
    ) {
        if (!rule.enabled) return

        // Resolved once per evaluate() call, not per episode - the preference can't change
        // mid-loop, and this is what makes every episode land on the user's actually-configured
        // download location instead of always ending up on storage index 0 regardless of it.
        val storageIndex = downloader.resolvePreferredStorageIndex()

        // Only gates this evaluator/PendingDownloadFulfiller - manual downloads are never capped.
        // Tracked as a running total (rather than re-reading getTotalDownloadedBytes() per
        // episode) so a rule matching many episodes at once stops as soon as the cumulative
        // estimate crosses the cap, instead of firing every match and only then discovering the
        // overshoot.
        val maxSizeEnabled = appPreferences.getValue(appPreferences.maxDownloadSizeEnabled)
        val maxSizeBytes =
            appPreferences.getValue(appPreferences.maxDownloadSizeGb) * Constants.BYTES_PER_GIB
        var totalDownloadedBytes = if (maxSizeEnabled) downloader.getTotalDownloadedBytes() else 0L

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

                    if (
                        !coversEpisode(
                            rule,
                            episode,
                            effectiveOnlyUnwatched = onlyUnwatched || rule.onlyUnwatched,
                        )
                    ) {
                        continue
                    }

                    if (maxSizeEnabled && totalDownloadedBytes >= maxSizeBytes) {
                        Timber.i(
                            "Max download size reached - skipping remaining auto-downloads for rule ${rule.id}"
                        )
                        return
                    }

                    val source = episode.sources.firstOrNull() ?: continue
                    downloader.downloadItem(episode, source.id, storageIndex = storageIndex)
                    totalDownloadedBytes += source.size
                } catch (e: Exception) {
                    Timber.e(e, "Failed to queue download for episode ${episode.id}")
                }
            }
        }
    }
}

/**
 * Whether [rule] would download [episode] right now - the same enabled/scope/onlyUnwatched/
 * onlyNewEpisodes filters [AutoDownloadRuleEvaluator.evaluate] applies while enumerating a whole
 * season fetched from the server, but as a standalone check against a single already-known
 * episode. Doesn't check whether a source already exists for [episode] - callers that care (both
 * [AutoDownloadRuleEvaluator.evaluate] and
 * [dev.pschmitt.jellyfin.work.NewItemNotificationWorker], which uses this to decide whether a new-
 * episode notification's "Download" action would be redundant - the episode will be
 * auto-downloaded anyway) check that themselves via `ServerDatabaseDao.getSources`.
 *
 * [effectiveOnlyUnwatched] mirrors [AutoDownloadRuleEvaluator.evaluate]'s own extra
 * `onlyUnwatched` parameter (an immediate one-off download can request unwatched-only even for a
 * rule that doesn't have that flag set) - defaults to the rule's own flag for callers with no
 * extra requirement of their own.
 */
fun coversEpisode(
    rule: AutoDownloadRuleDto,
    episode: FindroidEpisode,
    effectiveOnlyUnwatched: Boolean = rule.onlyUnwatched,
): Boolean {
    if (!rule.enabled) return false
    if (rule.seriesId != episode.seriesId) return false
    if (rule.seasonId != null && rule.seasonId != episode.seasonId) return false
    if (effectiveOnlyUnwatched && episode.played) return false

    // In onlyNewEpisodes mode, the rule never backfills the existing catalog - only episodes that
    // premiered after the rule was created are covered. An unknown premiere date is treated as
    // covered rather than silently excluded.
    val premiereDate = episode.premiereDate
    if (rule.onlyNewEpisodes && premiereDate != null) {
        val ruleCreatedAt =
            Instant.ofEpochMilli(rule.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
        if (premiereDate.isBefore(ruleCreatedAt)) return false
    }

    return true
}
