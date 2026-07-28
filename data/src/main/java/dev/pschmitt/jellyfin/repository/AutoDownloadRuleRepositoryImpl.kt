package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.AutoDownloadRuleDto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoDownloadRuleRepositoryImpl(private val database: ServerDatabaseDao) :
    AutoDownloadRuleRepository {

    override suspend fun setShowRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        enabled: Boolean,
    ): AutoDownloadRuleDto =
        withContext(Dispatchers.IO) {
            val existing = database.getShowAutoDownloadRule(serverId, userId, seriesId)
            val rule =
                upsertRule(existing, serverId, userId, seriesId, seasonId = null, enabled = enabled)
            if (enabled) {
                // A show-level rule supersedes any season-level rules for the same show.
                database.deleteSeasonAutoDownloadRulesForShow(serverId, userId, seriesId)
            }
            rule
        }

    override suspend fun setSeasonRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
        enabled: Boolean,
    ): AutoDownloadRuleDto =
        withContext(Dispatchers.IO) {
            val existing = database.getSeasonAutoDownloadRule(serverId, userId, seriesId, seasonId)
            upsertRule(existing, serverId, userId, seriesId, seasonId, enabled)
        }

    private fun upsertRule(
        existing: AutoDownloadRuleDto?,
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID?,
        enabled: Boolean,
    ): AutoDownloadRuleDto {
        if (existing != null) {
            val updated = existing.copy(enabled = enabled)
            database.updateAutoDownloadRule(updated)
            return updated
        }
        val rule =
            AutoDownloadRuleDto(
                serverId = serverId,
                userId = userId,
                seriesId = seriesId,
                seasonId = seasonId,
                enabled = enabled,
                createdAt = System.currentTimeMillis(),
            )
        val id = database.insertAutoDownloadRule(rule)
        return rule.copy(id = id)
    }

    override suspend fun isShowRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ): Boolean =
        withContext(Dispatchers.IO) {
            database.getAutoDownloadRulesForShow(serverId, userId, seriesId).any { it.enabled }
        }

    override suspend fun isSeasonRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
    ): Boolean =
        withContext(Dispatchers.IO) {
            database.getSeasonAutoDownloadRule(serverId, userId, seriesId, seasonId)?.enabled
                ?: false
        }

    override suspend fun getRules(serverId: String, userId: UUID): List<AutoDownloadRuleDto> =
        withContext(Dispatchers.IO) { database.getAutoDownloadRules(serverId, userId) }

    override suspend fun getRulesForSeries(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ): List<AutoDownloadRuleDto> =
        withContext(Dispatchers.IO) {
            database.getAutoDownloadRulesForShow(serverId, userId, seriesId)
        }

    override suspend fun getEnabledRules(
        serverId: String,
        userId: UUID,
    ): List<AutoDownloadRuleDto> =
        withContext(Dispatchers.IO) { database.getEnabledAutoDownloadRules(serverId, userId) }

    override suspend fun setRuleEnabled(id: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) { database.setAutoDownloadRuleEnabled(id, enabled) }

    override suspend fun setRuleOnlyNewEpisodes(id: Long, onlyNewEpisodes: Boolean) =
        withContext(Dispatchers.IO) {
            database.setAutoDownloadRuleOnlyNewEpisodes(id, onlyNewEpisodes)
        }

    override suspend fun setRuleOnlyUnwatched(id: Long, onlyUnwatched: Boolean) =
        withContext(Dispatchers.IO) {
            database.setAutoDownloadRuleOnlyUnwatched(id, onlyUnwatched)
        }

    override suspend fun reconcileRules(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFutureSeasons: Boolean,
        onlyNewEpisodes: Boolean,
        onlyUnwatched: Boolean,
    ): List<AutoDownloadRuleDto> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<AutoDownloadRuleDto>()

            if (seasonIds.isEmpty()) {
                database.deleteSeasonAutoDownloadRulesForShow(serverId, userId, seriesId)
            } else {
                database.deleteSeasonAutoDownloadRulesForShowExceptSeasons(
                    serverId,
                    userId,
                    seriesId,
                    seasonIds.toList(),
                )
                for (seasonId in seasonIds) {
                    val existing =
                        database.getSeasonAutoDownloadRule(serverId, userId, seriesId, seasonId)
                    val rule = upsertRule(existing, serverId, userId, seriesId, seasonId, enabled = true)
                    database.setAutoDownloadRuleOnlyNewEpisodes(rule.id, onlyNewEpisodes)
                    database.setAutoDownloadRuleOnlyUnwatched(rule.id, onlyUnwatched)
                    result += rule.copy(onlyNewEpisodes = onlyNewEpisodes, onlyUnwatched = onlyUnwatched)
                }
            }

            val existingShowRule = database.getShowAutoDownloadRule(serverId, userId, seriesId)
            if (alsoFutureSeasons) {
                val rule =
                    upsertRule(
                        existingShowRule,
                        serverId,
                        userId,
                        seriesId,
                        seasonId = null,
                        enabled = true,
                    )
                // Nothing to backfill for a season that doesn't exist yet - always only-new.
                database.setAutoDownloadRuleOnlyNewEpisodes(rule.id, true)
                database.setAutoDownloadRuleOnlyUnwatched(rule.id, onlyUnwatched)
                result += rule.copy(onlyNewEpisodes = true, onlyUnwatched = onlyUnwatched)
            } else if (existingShowRule != null) {
                database.deleteAutoDownloadRule(existingShowRule.id)
            }

            result
        }

    override suspend fun deleteRule(id: Long) =
        withContext(Dispatchers.IO) { database.deleteAutoDownloadRule(id) }

    override suspend fun deleteRulesForShow(serverId: String, userId: UUID, seriesId: UUID) =
        withContext(Dispatchers.IO) {
            database.deleteAutoDownloadRulesForShow(serverId, userId, seriesId)
        }

    override suspend fun deleteSeasonRule(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
    ) =
        withContext(Dispatchers.IO) {
            database.getSeasonAutoDownloadRule(serverId, userId, seriesId, seasonId)?.let { rule ->
                database.deleteAutoDownloadRule(rule.id)
            }
            Unit
        }

    override suspend fun deleteAllRules(serverId: String, userId: UUID) =
        withContext(Dispatchers.IO) { database.deleteAllAutoDownloadRules(serverId, userId) }
}
