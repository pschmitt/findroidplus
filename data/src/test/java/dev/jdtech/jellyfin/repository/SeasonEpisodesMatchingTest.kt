package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.SonarrEpisodeDto
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeasonEpisodesMatchingTest {

    private lateinit var originalDefaultTimeZone: TimeZone

    @Before
    fun setUp() {
        originalDefaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    @Test
    fun `returns only episodes of the requested season missing from the known set`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 1, title = "Pilot"),
                SonarrEpisodeDto(id = 2, seasonNumber = 1, episodeNumber = 2, title = "Episode Two"),
                SonarrEpisodeDto(id = 3, seasonNumber = 2, episodeNumber = 1, title = "Other Season"),
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = setOf(1))

        assertEquals(1, result.size)
        assertEquals(2, result[0].episodeNumber)
        assertEquals("Episode Two", result[0].title)
        assertEquals(2, result[0].episodeId)
    }

    @Test
    fun `is sorted by episode number regardless of input order`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 3),
                SonarrEpisodeDto(id = 2, seasonNumber = 1, episodeNumber = 1),
                SonarrEpisodeDto(id = 3, seasonNumber = 1, episodeNumber = 2),
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        assertEquals(listOf(1, 2, 3), result.map { it.episodeNumber })
    }

    @Test
    fun `blank title becomes null rather than an empty string`() {
        val episodes = listOf(SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 1, title = ""))

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        assertEquals(null, result.single().title)
    }

    @Test
    fun `parses airDateUtc into a LocalDate`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(
                    id = 1,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    airDateUtc = "2024-07-24T01:00:00Z",
                )
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        assertEquals(LocalDate.of(2024, 7, 24), result.single().airDate)
    }

    @Test
    fun `parses airDateUtc into an exact local airTime`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(
                    id = 1,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    airDateUtc = "2024-07-24T01:00:00Z",
                )
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        // Default TimeZone is fixed to UTC in setUp(), so the instant's UTC time and its
        // system-default-zone-converted LocalTime line up exactly.
        assertEquals(LocalTime.of(1, 0), result.single().airTime)
    }

    @Test
    fun `airTime is null for a date-only airDateUtc`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(
                    id = 1,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    airDateUtc = "2024-07-24",
                )
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        assertEquals(null, result.single().airTime)
    }

    @Test
    fun `carries hasFile and monitored through unchanged`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(
                    id = 1,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    hasFile = true,
                    monitored = false,
                )
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = emptySet())

        assertTrue(result.single().hasFile)
        assertEquals(false, result.single().monitored)
    }

    @Test
    fun `returns empty list when everything is already known`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 1, episodeNumber = 2),
            )

        val result = matchUpcomingEpisodes(episodes, seasonNumber = 1, knownEpisodeNumbers = setOf(1, 2))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchMissingSeasons returns only season numbers missing from the known set`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 2, episodeNumber = 1),
                SonarrEpisodeDto(id = 3, seasonNumber = 2, episodeNumber = 2),
                SonarrEpisodeDto(id = 4, seasonNumber = 3, episodeNumber = 1),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = setOf(1))

        assertEquals(listOf(2, 3), result.map { it.seasonNumber })
    }

    @Test
    fun `matchMissingSeasons counts episodes per missing season`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 4, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 4, episodeNumber = 2),
                SonarrEpisodeDto(id = 3, seasonNumber = 4, episodeNumber = 3),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = emptySet())

        assertEquals(1, result.size)
        assertEquals(3, result.single().episodeCount)
    }

    @Test
    fun `matchMissingSeasons is monitored when any of its episodes are monitored`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 4, episodeNumber = 1, monitored = false),
                SonarrEpisodeDto(id = 2, seasonNumber = 4, episodeNumber = 2, monitored = true),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = emptySet())

        assertTrue(result.single().monitored)
    }

    @Test
    fun `matchMissingSeasons excludes season 0 specials`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 0, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 1, episodeNumber = 1),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = emptySet())

        assertEquals(listOf(1), result.map { it.seasonNumber })
    }

    @Test
    fun `matchMissingSeasons is sorted by season number regardless of input order`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 3, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 1, episodeNumber = 1),
                SonarrEpisodeDto(id = 3, seasonNumber = 2, episodeNumber = 1),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = emptySet())

        assertEquals(listOf(1, 2, 3), result.map { it.seasonNumber })
    }

    @Test
    fun `matchMissingSeasons returns empty list when everything is already known`() {
        val episodes =
            listOf(
                SonarrEpisodeDto(id = 1, seasonNumber = 1, episodeNumber = 1),
                SonarrEpisodeDto(id = 2, seasonNumber = 2, episodeNumber = 1),
            )

        val result = matchMissingSeasons(episodes, knownSeasonNumbers = setOf(1, 2))

        assertTrue(result.isEmpty())
    }
}
