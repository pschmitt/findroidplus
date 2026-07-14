package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
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

    suspend fun getEnabledRules(serverId: String, userId: UUID): List<AutoDownloadRuleDto>

    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    suspend fun setRuleOnlyNewEpisodes(id: Long, onlyNewEpisodes: Boolean)

    suspend fun deleteRule(id: Long)

    suspend fun deleteRulesForShow(serverId: String, userId: UUID, seriesId: UUID)

    suspend fun deleteAllRules(serverId: String, userId: UUID)
}
