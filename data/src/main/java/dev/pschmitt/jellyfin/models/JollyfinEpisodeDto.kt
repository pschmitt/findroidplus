package dev.pschmitt.jellyfin.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(
    tableName = "episodes",
    foreignKeys =
        [
            ForeignKey(
                entity = JollyfinSeasonDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seasonId"),
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = JollyfinShowDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seriesId"),
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("seasonId"), Index("seriesId")],
)
data class JollyfinEpisodeDto(
    @PrimaryKey val id: UUID,
    val serverId: String?,
    val seasonId: UUID,
    val seriesId: UUID,
    val name: String,
    val seriesName: String,
    val overview: String,
    val indexNumber: Int,
    val indexNumberEnd: Int?,
    val parentIndexNumber: Int,
    val runtimeTicks: Long,
    val premiereDate: LocalDateTime?,
    val communityRating: Float?,
    val chapters: List<JollyfinChapter>?,
)

fun JollyfinEpisode.toJollyfinEpisodeDto(serverId: String? = null): JollyfinEpisodeDto {
    return JollyfinEpisodeDto(
        id = id,
        serverId = serverId,
        seasonId = seasonId,
        seriesId = seriesId,
        name = name,
        seriesName = seriesName,
        overview = overview,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
        parentIndexNumber = parentIndexNumber,
        runtimeTicks = runtimeTicks,
        premiereDate = premiereDate,
        communityRating = communityRating,
        chapters = chapters,
    )
}
