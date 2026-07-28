package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.AutoDownloadRuleDto
import java.util.UUID

interface AutoDownloadRuleRepository {
    suspend fun setShowRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        enabled: Boolean,
    ): AutoDownloadRuleDto

    suspend fun setSeasonRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
        enabled: Boolean,
    ): AutoDownloadRuleDto

    suspend fun isShowRuleEnabled(serverId: String, userId: UUID, seriesId: UUID): Boolean

    suspend fun isSeasonRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
    ): Boolean

    suspend fun getRules(serverId: String, userId: UUID): List<AutoDownloadRuleDto>

    suspend fun getRulesForSeries(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ): List<AutoDownloadRuleDto>

    suspend fun getEnabledRules(serverId: String, userId: UUID): List<AutoDownloadRuleDto>

    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    suspend fun setRuleOnlyNewEpisodes(id: Long, onlyNewEpisodes: Boolean)

    suspend fun setRuleOnlyUnwatched(id: Long, onlyUnwatched: Boolean)

    /**
     * Replaces every season-specific rule for [seriesId] with exactly one rule per id in
     * [seasonIds], and independently ensures a show-level "auto-download future seasons" rule
     * exists iff [alsoFutureSeasons] is true - the two are no longer mutually exclusive, since a
     * season row and the show-level (seasonId IS NULL) row can coexist. [onlyNewEpisodes] applies
     * to the season-specific rules; the future-seasons rule is always only-new by definition
     * (there's nothing to backfill for a season that doesn't exist yet). [onlyUnwatched] applies
     * to every resulting rule.
     */
    suspend fun reconcileRules(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFutureSeasons: Boolean,
        onlyNewEpisodes: Boolean,
        onlyUnwatched: Boolean,
    ): List<AutoDownloadRuleDto>

    suspend fun deleteRule(id: Long)

    suspend fun deleteRulesForShow(serverId: String, userId: UUID, seriesId: UUID)

    suspend fun deleteSeasonRule(serverId: String, userId: UUID, seriesId: UUID, seasonId: UUID)

    suspend fun deleteAllRules(serverId: String, userId: UUID)
}

/** The picked scope (seasons + future-seasons + follow-up behavior) an existing set of rules for a series represents - reconstructed from the raw DB rows so a dialog can be reopened pre-populated with what's already saved. */
data class ExistingAutoDownloadScope(
    val seasonIds: Set<UUID> = emptySet(),
    val alsoFutureSeasons: Boolean = false,
    val alsoFollowNew: Boolean = false,
    val onlyUnwatched: Boolean = false,
)

fun List<AutoDownloadRuleDto>.toExistingScope(): ExistingAutoDownloadScope {
    val seasonIds = mapNotNull { it.seasonId }.toSet()
    return ExistingAutoDownloadScope(
        seasonIds = seasonIds,
        alsoFutureSeasons = any { it.seasonId == null && it.enabled },
        // A season-specific rule only ever exists in the DB as a persisted "keep following new
        // episodes" rule - a one-off, non-persisted download never creates a row - so its mere
        // presence means alsoFollowNew was on when it was created.
        alsoFollowNew = seasonIds.isNotEmpty(),
        onlyUnwatched = firstOrNull()?.onlyUnwatched ?: false,
    )
}
