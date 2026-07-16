package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrCalendarEntry
import dev.jdtech.jellyfin.api.pvr.SonarrCalendarEntry
import dev.jdtech.jellyfin.api.pvr.SonarrCalendarSeries
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.PvrSource
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalendarMatchingTest {

    private lateinit var originalDefaultTimeZone: TimeZone

    @Before
    fun setUp() {
        // matchSonarrCalendar converts airDateUtc (an Instant) to a LocalDate in the system
        // default time zone (see parseFlexibleDate) - pinned to UTC here so these tests give the
        // same result no matter which time zone the machine running them is in.
        originalDefaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone)
    }

    // region matchSonarrCalendar

    @Test
    fun `matchSonarrCalendar resolves an exact match through tvdbId`() {
        val showId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = "1000", name = "House of the Dragon")

        val entries =
            listOf(
                SonarrCalendarEntry(
                    id = 1,
                    seriesId = 1,
                    seasonNumber = 3,
                    episodeNumber = 5,
                    title = "The Choice",
                    airDateUtc = "2024-07-24T01:00:00Z",
                    hasFile = false,
                    monitored = true,
                    series = SonarrCalendarSeries(tvdbId = 1000, title = "House of the Dragon"),
                )
            )

        val result = matchSonarrCalendar(entries, listOf(show))

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals(PvrSource.SONARR, entry.source)
        assertEquals(showId, entry.itemId)
        assertEquals("House of the Dragon", entry.title)
        assertEquals("S03E05 - The Choice", entry.subtitle)
        assertEquals(LocalDate.of(2024, 7, 24), entry.date)
        assertEquals(false, entry.hasFile)
        assertTrue(entry.monitored)
    }

    @Test
    fun `matchSonarrCalendar still includes an entry with itemId null when neither side has a tvdbId`() {
        val show = testShow(id = UUID.randomUUID(), tvdbId = null, name = "Some Other Show")

        // SonarrCalendarSeries.tvdbId defaults to 0, the DTO's "unset" sentinel.
        val entries =
            listOf(
                SonarrCalendarEntry(
                    id = 1,
                    seriesId = 1,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Pilot",
                    airDateUtc = "2024-07-24T01:00:00Z",
                    series = SonarrCalendarSeries(title = "Unmatched Show"),
                )
            )

        val result = matchSonarrCalendar(entries, listOf(show))

        assertEquals(1, result.size)
        assertNull(result[0].itemId)
        assertEquals("Unmatched Show", result[0].title)
    }

    @Test
    fun `matchSonarrCalendar still includes an entry when the series is absent entirely`() {
        val entries =
            listOf(
                SonarrCalendarEntry(
                    id = 1,
                    seriesId = 1,
                    seasonNumber = 2,
                    episodeNumber = 3,
                    title = null,
                    airDateUtc = "2024-07-24T01:00:00Z",
                    series = null,
                )
            )

        val result = matchSonarrCalendar(entries, emptyList())

        assertEquals(1, result.size)
        assertNull(result[0].itemId)
        assertEquals("", result[0].title)
        assertEquals("S02E03", result[0].subtitle)
    }

    @Test
    fun `matchSonarrCalendar skips an entry with no parseable air date`() {
        val entries = listOf(SonarrCalendarEntry(id = 1, seriesId = 1, airDateUtc = null))

        val result = matchSonarrCalendar(entries, emptyList())

        assertTrue(result.isEmpty())
    }

    // endregion

    // region matchRadarrCalendar

    @Test
    fun `matchRadarrCalendar resolves an exact match through tmdbId`() {
        val movieId = UUID.randomUUID()
        val movie = testMovie(id = movieId, tmdbId = "2000", name = "Dune: Part Two")

        val entries =
            listOf(
                RadarrCalendarEntry(
                    id = 1,
                    tmdbId = 2000,
                    title = "Dune: Part Two",
                    hasFile = true,
                    monitored = true,
                    digitalRelease = "2024-07-24T00:00:00Z",
                )
            )

        val result =
            matchRadarrCalendar(
                entries,
                listOf(movie),
                start = LocalDate.of(2024, 7, 1),
                end = LocalDate.of(2024, 8, 1),
            )

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals(PvrSource.RADARR, entry.source)
        assertEquals(movieId, entry.itemId)
        assertEquals("Dune: Part Two", entry.title)
        assertNull(entry.subtitle)
        assertEquals(LocalDate.of(2024, 7, 24), entry.date)
        assertTrue(entry.hasFile)
    }

    @Test
    fun `matchRadarrCalendar still includes an entry with itemId null when neither side has a tmdbId`() {
        val movie = testMovie(id = UUID.randomUUID(), tmdbId = null, name = "Some Other Movie")

        // RadarrCalendarEntry.tmdbId defaults to 0, the DTO's "unset" sentinel.
        val entries =
            listOf(
                RadarrCalendarEntry(id = 1, title = "Unmatched Movie", inCinemas = "2024-07-24T00:00:00Z")
            )

        val result =
            matchRadarrCalendar(
                entries,
                listOf(movie),
                start = LocalDate.of(2024, 7, 1),
                end = LocalDate.of(2024, 8, 1),
            )

        assertEquals(1, result.size)
        assertNull(result[0].itemId)
        assertEquals("Unmatched Movie", result[0].title)
    }

    @Test
    fun `matchRadarrCalendar skips an entry with no usable release date at all`() {
        val entries = listOf(RadarrCalendarEntry(id = 1, title = "No Dates"))

        val result =
            matchRadarrCalendar(
                entries,
                emptyList(),
                start = LocalDate.of(2024, 7, 1),
                end = LocalDate.of(2024, 8, 1),
            )

        assertTrue(result.isEmpty())
    }

    // endregion

    // region selectRadarrDate (Radarr's three-date fallback)

    @Test
    fun `selectRadarrDate uses inCinemas when it is the only date set`() {
        val entry = RadarrCalendarEntry(id = 1, inCinemas = "2024-07-10T00:00:00Z")

        val date = selectRadarrDate(entry, start = LocalDate.of(2024, 7, 1), end = LocalDate.of(2024, 8, 1))

        assertEquals(LocalDate.of(2024, 7, 10), date)
    }

    @Test
    fun `selectRadarrDate uses digitalRelease when it is the only date set`() {
        val entry = RadarrCalendarEntry(id = 1, digitalRelease = "2024-07-15T00:00:00Z")

        val date = selectRadarrDate(entry, start = LocalDate.of(2024, 7, 1), end = LocalDate.of(2024, 8, 1))

        assertEquals(LocalDate.of(2024, 7, 15), date)
    }

    @Test
    fun `selectRadarrDate prefers the earliest in-range date when multiple are set`() {
        val entry =
            RadarrCalendarEntry(
                id = 1,
                inCinemas = "2024-06-01T00:00:00Z",
                digitalRelease = "2024-07-20T00:00:00Z",
                physicalRelease = "2024-07-15T00:00:00Z",
            )

        // inCinemas (June 1) is outside [start, end]; between the two in-range dates,
        // physicalRelease (July 15) is earlier than digitalRelease (July 20).
        val date = selectRadarrDate(entry, start = LocalDate.of(2024, 7, 1), end = LocalDate.of(2024, 8, 1))

        assertEquals(LocalDate.of(2024, 7, 15), date)
    }

    @Test
    fun `selectRadarrDate falls back to the first non-null date in preference order when none are in range`() {
        val entry = RadarrCalendarEntry(id = 1, inCinemas = "2023-01-01T00:00:00Z")

        val date = selectRadarrDate(entry, start = LocalDate.of(2024, 7, 1), end = LocalDate.of(2024, 8, 1))

        assertEquals(LocalDate.of(2023, 1, 1), date)
    }

    @Test
    fun `selectRadarrDate returns null when all three dates are null`() {
        val entry = RadarrCalendarEntry(id = 1)

        val date = selectRadarrDate(entry, start = LocalDate.of(2024, 7, 1), end = LocalDate.of(2024, 8, 1))

        assertNull(date)
    }

    // endregion

    // region sorting (mixed Sonarr+Radarr entries)

    @Test
    fun `combined Sonarr and Radarr results interleave correctly by date once sorted`() {
        val sonarrEntries =
            listOf(
                SonarrCalendarEntry(id = 1, airDateUtc = "2024-07-24T00:00:00Z", title = "Episode A"),
                SonarrCalendarEntry(id = 2, airDateUtc = "2024-07-10T00:00:00Z", title = "Episode B"),
            )
        val radarrEntries =
            listOf(RadarrCalendarEntry(id = 1, title = "Movie A", digitalRelease = "2024-07-17T00:00:00Z"))

        val sonarrResult = matchSonarrCalendar(sonarrEntries, emptyList())
        val radarrResult =
            matchRadarrCalendar(
                radarrEntries,
                emptyList(),
                start = LocalDate.of(2024, 7, 1),
                end = LocalDate.of(2024, 8, 1),
            )

        val combined = (sonarrResult + radarrResult).sortedBy { it.date }

        assertEquals(3, combined.size)
        assertEquals(LocalDate.of(2024, 7, 10), combined[0].date)
        assertEquals("", combined[0].title) // Episode B, no series -> blank title
        assertEquals(LocalDate.of(2024, 7, 17), combined[1].date)
        assertEquals("Movie A", combined[1].title)
        assertEquals(LocalDate.of(2024, 7, 24), combined[2].date)
    }

    // endregion

    // region one-service-down independence (documented at the repository level -
    // CalendarRepositoryImpl.fetchSonarrCalendar()/fetchRadarrCalendar() each wrap their own
    // fetch+match in try/catch, so an exception thrown while calling Sonarr/Radarr never affects
    // the other service's contribution to the merged list - mirrors QueueStatusRepositoryImpl.
    // The matching functions under test here are pure and never throw regardless of input, by
    // construction - every lookup that fails just falls back to itemId = null, never a `!!` or an
    // index that can be missing.)

    // endregion

    private fun testShow(id: UUID, tvdbId: String?, name: String): FindroidShow =
        FindroidShow(
            id = id,
            name = name,
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            seasons = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            genres = emptyList(),
            people = emptyList(),
            runtimeTicks = 0L,
            communityRating = null,
            officialRating = null,
            status = "Continuing",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
            tvdbId = tvdbId,
        )

    private fun testMovie(id: UUID, tmdbId: String?, name: String): FindroidMovie =
        FindroidMovie(
            id = id,
            name = name,
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            people = emptyList(),
            genres = emptyList(),
            communityRating = null,
            officialRating = null,
            status = "Released",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
            tmdbId = tmdbId,
        )
}
