package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
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
            database.getShowAutoDownloadRule(serverId, userId, seriesId)?.enabled ?: false
        }

    override suspend fun isSeasonRuleEnabled(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val seasonRuleEnabled =
                database.getSeasonAutoDownloadRule(serverId, userId, seriesId, seasonId)?.enabled
                    ?: false
            // A show-level rule already covers every season, including this one.
            seasonRuleEnabled ||
                (database.getShowAutoDownloadRule(serverId, userId, seriesId)?.enabled ?: false)
        }

    override suspend fun getRules(serverId: String, userId: UUID): List<AutoDownloadRuleDto> =
        withContext(Dispatchers.IO) { database.getAutoDownloadRules(serverId, userId) }

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

    override suspend fun deleteRule(id: Long) =
        withContext(Dispatchers.IO) { database.deleteAutoDownloadRule(id) }

    override suspend fun deleteRulesForShow(serverId: String, userId: UUID, seriesId: UUID) =
        withContext(Dispatchers.IO) {
            database.deleteAutoDownloadRulesForShow(serverId, userId, seriesId)
        }

    override suspend fun deleteAllRules(serverId: String, userId: UUID) =
        withContext(Dispatchers.IO) { database.deleteAllAutoDownloadRules(serverId, userId) }
}
