package dev.pschmitt.jellyfin.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "shows")
data class JollyfinShowDto(
    @PrimaryKey val id: UUID,
    val serverId: String?,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val communityRating: Float?,
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val tvdbId: String? = null,
)

fun JollyfinShow.toJollyfinShowDto(serverId: String? = null): JollyfinShowDto {
    return JollyfinShowDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
        tvdbId = tvdbId,
    )
}
