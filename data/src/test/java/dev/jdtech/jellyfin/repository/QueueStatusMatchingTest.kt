package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrMovie
import dev.jdtech.jellyfin.api.pvr.RadarrQueueItem
import dev.jdtech.jellyfin.api.pvr.SonarrEpisode
import dev.jdtech.jellyfin.api.pvr.SonarrQueueItem
import dev.jdtech.jellyfin.api.pvr.SonarrSeries
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueItemStatus
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStatusMatchingTest {

    // region matchSonarr

    @Test
    fun `matchSonarr resolves an exact match through tvdbId then season-episode number`() {
        val showId = UUID.randomUUID()
        val episodeId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = "1000")
        val episode = testEpisode(id = episodeId, seriesId = showId, season = 1, index = 5)

        val series = listOf(SonarrSeries(id = 1, tvdbId = 1000))
        val queue =
            listOf(
                SonarrQueueItem(
                    id = 42,
                    seriesId = 1,
                    seasonNumber = 1,
                    episode = SonarrEpisode(episodeNumber = 5),
                    status = "downloading",
                    size = 1000,
                    sizeleft = 250,
                )
            )

        val result =
            matchSonarr(
                series = series,
                queue = queue,
                jellyfinShows = listOf(show),
                episodesByShowId = mapOf(showId to listOf(episode)),
            )

        assertEquals(1, result.size)
        val status = result[episodeId]
        assertEquals(PvrSource.SONARR, status?.source)
        assertEquals(QueueItemStatus.DOWNLOADING, status?.status)
        assertEquals(75, status?.percent)
    }

    @Test
    fun `matchSonarr skips silently when neither side has a tvdbId`() {
        val showId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = null)
        val episode = testEpisode(id = UUID.randomUUID(), seriesId = showId, season = 1, index = 5)

        // SonarrSeries.tvdbId defaults to 0, the DTO's "unset" sentinel.
        val series = listOf(SonarrSeries(id = 1))
        val queue =
            listOf(SonarrQueueItem(id = 42, seriesId = 1, seasonNumber = 1, episode = SonarrEpisode(5)))

        val result =
            matchSonarr(series, queue, listOf(show), mapOf(showId to listOf(episode)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchSonarr skips a queue entry whose seriesId is not in the series list at all`() {
        val showId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = "1000")
        val episode = testEpisode(id = UUID.randomUUID(), seriesId = showId, season = 1, index = 5)

        val series = listOf(SonarrSeries(id = 1, tvdbId = 1000))
        // References seriesId 999, which doesn't exist in `series` - an orphaned reference.
        val queue =
            listOf(SonarrQueueItem(id = 42, seriesId = 999, seasonNumber = 1, episode = SonarrEpisode(5)))

        val result =
            matchSonarr(series, queue, listOf(show), mapOf(showId to listOf(episode)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchSonarr skips when the episode is not yet synced into Jellyfin's library`() {
        val showId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = "1000")

        val series = listOf(SonarrSeries(id = 1, tvdbId = 1000))
        val queue =
            listOf(SonarrQueueItem(id = 42, seriesId = 1, seasonNumber = 1, episode = SonarrEpisode(5)))

        // episodesByShowId has no entry at all for showId - show exists, but nothing has synced yet.
        val result = matchSonarr(series, queue, listOf(show), emptyMap())

        assertTrue(result.isEmpty())

        // Also covers the case where the show's episode list exists but simply doesn't (yet)
        // contain the queued episode.
        val otherEpisode = testEpisode(id = UUID.randomUUID(), seriesId = showId, season = 1, index = 1)
        val result2 =
            matchSonarr(series, queue, listOf(show), mapOf(showId to listOf(otherEpisode)))
        assertTrue(result2.isEmpty())
    }

    @Test
    fun `matchSonarr last queue entry wins when two entries resolve to the same episode`() {
        val showId = UUID.randomUUID()
        val episodeId = UUID.randomUUID()
        val show = testShow(id = showId, tvdbId = "1000")
        val episode = testEpisode(id = episodeId, seriesId = showId, season = 1, index = 5)

        val series = listOf(SonarrSeries(id = 1, tvdbId = 1000))
        val queue =
            listOf(
                SonarrQueueItem(
                    id = 1,
                    seriesId = 1,
                    seasonNumber = 1,
                    episode = SonarrEpisode(5),
                    status = "downloading",
                ),
                // A retried download for the same episode - a second queue row before Sonarr
                // cleans up the first one.
                SonarrQueueItem(
                    id = 2,
                    seriesId = 1,
                    seasonNumber = 1,
                    episode = SonarrEpisode(5),
                    status = "queued",
                ),
            )

        val result =
            matchSonarr(series, queue, listOf(show), mapOf(showId to listOf(episode)))

        assertEquals(1, result.size)
        assertEquals(QueueItemStatus.QUEUED, result[episodeId]?.status)
    }

    // endregion

    // region matchRadarr

    @Test
    fun `matchRadarr resolves an exact match through tmdbId`() {
        val movieId = UUID.randomUUID()
        val movie = testMovie(id = movieId, tmdbId = "2000")

        val movies = listOf(RadarrMovie(id = 7, tmdbId = 2000))
        val queue =
            listOf(RadarrQueueItem(id = 1, movieId = 7, status = "downloading", size = 1000, sizeleft = 100))

        val result = matchRadarr(movies, queue, listOf(movie))

        assertEquals(1, result.size)
        val status = result[movieId]
        assertEquals(PvrSource.RADARR, status?.source)
        assertEquals(QueueItemStatus.DOWNLOADING, status?.status)
        assertEquals(90, status?.percent)
    }

    @Test
    fun `matchRadarr skips silently when neither side has a tmdbId`() {
        val movie = testMovie(id = UUID.randomUUID(), tmdbId = null)
        // RadarrMovie.tmdbId defaults to 0, the DTO's "unset" sentinel.
        val movies = listOf(RadarrMovie(id = 7))
        val queue = listOf(RadarrQueueItem(id = 1, movieId = 7))

        val result = matchRadarr(movies, queue, listOf(movie))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchRadarr skips a queue entry whose movieId is not in the movie list at all`() {
        val movie = testMovie(id = UUID.randomUUID(), tmdbId = "2000")
        val movies = listOf(RadarrMovie(id = 7, tmdbId = 2000))
        // References movieId 999, which doesn't exist in `movies` - an orphaned reference.
        val queue = listOf(RadarrQueueItem(id = 1, movieId = 999))

        val result = matchRadarr(movies, queue, listOf(movie))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchRadarr skips when the movie is not yet synced into Jellyfin's library`() {
        val movies = listOf(RadarrMovie(id = 7, tmdbId = 2000))
        val queue = listOf(RadarrQueueItem(id = 1, movieId = 7))

        val result = matchRadarr(movies, queue, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchRadarr last queue entry wins when two entries resolve to the same movie`() {
        val movieId = UUID.randomUUID()
        val movie = testMovie(id = movieId, tmdbId = "2000")
        val movies = listOf(RadarrMovie(id = 7, tmdbId = 2000))
        val queue =
            listOf(
                RadarrQueueItem(id = 1, movieId = 7, status = "downloading"),
                RadarrQueueItem(id = 2, movieId = 7, status = "warning", trackedDownloadStatus = "warning"),
            )

        val result = matchRadarr(movies, queue, listOf(movie))

        assertEquals(1, result.size)
        assertEquals(QueueItemStatus.WARNING, result[movieId]?.status)
    }

    // endregion

    // region one-service-down independence (documents the try/catch structure covered at the
    // repository level - QueueStatusRepositoryImpl.fetchSonarrStatus()/fetchRadarrStatus() each
    // wrap their own fetch+match in try/catch, so an exception thrown while calling Sonarr/Radarr
    // never affects the other service's contribution to the merged map. The matching functions
    // under test here are pure and never throw regardless of input, by construction - every
    // lookup that fails is a `continue`, never a `!!` or an index that can be missing.)

    // endregion

    // region status mapping

    @Test
    fun `mapQueueItemStatus maps trackedDownloadStatus error to FAILED regardless of status`() {
        assertEquals(
            QueueItemStatus.FAILED,
            mapQueueItemStatus(status = "downloading", trackedDownloadStatus = "error", trackedDownloadState = null),
        )
    }

    @Test
    fun `mapQueueItemStatus maps trackedDownloadStatus warning to WARNING regardless of status`() {
        assertEquals(
            QueueItemStatus.WARNING,
            mapQueueItemStatus(status = "downloading", trackedDownloadStatus = "warning", trackedDownloadState = null),
        )
    }

    @Test
    fun `mapQueueItemStatus maps queued-ish statuses to QUEUED`() {
        for (status in listOf("queued", "delay", "paused", null)) {
            assertEquals(
                "status=$status",
                QueueItemStatus.QUEUED,
                mapQueueItemStatus(status = status, trackedDownloadStatus = null, trackedDownloadState = null),
            )
        }
    }

    @Test
    fun `mapQueueItemStatus maps downloading to DOWNLOADING`() {
        assertEquals(
            QueueItemStatus.DOWNLOADING,
            mapQueueItemStatus(status = "downloading", trackedDownloadStatus = "ok", trackedDownloadState = "downloading"),
        )
    }

    @Test
    fun `mapQueueItemStatus maps completed or import tracked states to IMPORTING`() {
        assertEquals(
            QueueItemStatus.IMPORTING,
            mapQueueItemStatus(status = "completed", trackedDownloadStatus = "ok", trackedDownloadState = null),
        )
        assertEquals(
            QueueItemStatus.IMPORTING,
            mapQueueItemStatus(status = "downloading", trackedDownloadStatus = "ok", trackedDownloadState = "importPending"),
        )
    }

    @Test
    fun `mapQueueItemStatus maps failed status or tracked state to FAILED`() {
        assertEquals(
            QueueItemStatus.FAILED,
            mapQueueItemStatus(status = "failed", trackedDownloadStatus = null, trackedDownloadState = null),
        )
        assertEquals(
            QueueItemStatus.FAILED,
            mapQueueItemStatus(status = "downloading", trackedDownloadStatus = "ok", trackedDownloadState = "failedPending"),
        )
    }

    @Test
    fun `parseTimeleftSeconds parses HH-MM-SS and day-prefixed durations, and blanks as unknown`() {
        assertEquals(3661L, parseTimeleftSeconds("01:01:01"))
        assertEquals(90_061L, parseTimeleftSeconds("1.01:01:01"))
        assertEquals(-1L, parseTimeleftSeconds(null))
        assertEquals(-1L, parseTimeleftSeconds(""))
        assertEquals(-1L, parseTimeleftSeconds("not-a-duration"))
    }

    // endregion

    private fun testShow(id: UUID, tvdbId: String?): FindroidShow =
        FindroidShow(
            id = id,
            name = "Show $id",
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

    private fun testEpisode(id: UUID, seriesId: UUID, season: Int, index: Int): FindroidEpisode =
        FindroidEpisode(
            id = id,
            name = "Episode $id",
            originalTitle = null,
            overview = "",
            indexNumber = index,
            indexNumberEnd = null,
            parentIndexNumber = season,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = UUID.randomUUID(),
            seasonName = null,
            communityRating = null,
            people = emptyList(),
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun testMovie(id: UUID, tmdbId: String?): FindroidMovie =
        FindroidMovie(
            id = id,
            name = "Movie $id",
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
