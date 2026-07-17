package dev.jdtech.jellyfin.api.pvr

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SonarrApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = SonarrApi(baseUrl = server.url("/").toString(), apiKey = "test-api-key")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getSeries decodes only the fields we need and ignores the rest`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                    {
                        "id": 1,
                        "tvdbId": 12345,
                        "tmdbId": 67890,
                        "title": "Some Show",
                        "someUnknownField": {"nested": true},
                        "seasons": [{"seasonNumber": 1}]
                    }
                ]
                """
                    .trimIndent()
            )
        )

        val series = api.getSeries()

        assertEquals(1, series.size)
        assertEquals(1, series[0].id)
        assertEquals(12345, series[0].tvdbId)
        assertEquals(67890, series[0].tmdbId)
        assertEquals("Some Show", series[0].title)
    }

    @Test
    fun `getQueue unwraps the paginated records list and requests a large page size`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "page": 1,
                    "pageSize": 250,
                    "totalRecords": 1,
                    "records": [
                        {
                            "id": 42,
                            "seriesId": 1,
                            "episodeId": 10,
                            "seasonNumber": 2,
                            "status": "downloading",
                            "size": 1000,
                            "sizeleft": 250
                        }
                    ]
                }
                """
                    .trimIndent()
            )
        )

        val queue = api.getQueue()

        assertEquals(1, queue.size)
        assertEquals(42, queue[0].id)
        assertEquals(1, queue[0].seriesId)
        assertEquals(10, queue[0].episodeId)
        assertEquals(2, queue[0].seasonNumber)
        assertEquals("downloading", queue[0].status)

        val recordedRequest = server.takeRequest()
        assertEquals("test-api-key", recordedRequest.getHeader("X-Api-Key"))
        assertTrue(recordedRequest.path.orEmpty().contains("pageSize=250"))
    }

    @Test
    fun `getCalendar decodes a flat array and requests start, end and includeSeries`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                    {
                        "id": 55,
                        "seriesId": 1,
                        "seasonNumber": 3,
                        "episodeNumber": 5,
                        "title": "Some Episode",
                        "airDateUtc": "2024-07-24T01:00:00Z",
                        "hasFile": false,
                        "monitored": true,
                        "series": {"tvdbId": 12345, "title": "Some Show"}
                    }
                ]
                """
                    .trimIndent()
            )
        )

        val entries = api.getCalendar(LocalDate.of(2024, 7, 21), LocalDate.of(2024, 8, 20))

        assertEquals(1, entries.size)
        assertEquals(55, entries[0].id)
        assertEquals(3, entries[0].seasonNumber)
        assertEquals(5, entries[0].episodeNumber)
        assertEquals(12345, entries[0].series?.tvdbId)

        val recordedRequest = server.takeRequest()
        assertEquals("test-api-key", recordedRequest.getHeader("X-Api-Key"))
        assertTrue(recordedRequest.path.orEmpty().contains("start=2024-07-21"))
        assertTrue(recordedRequest.path.orEmpty().contains("end=2024-08-20"))
        assertTrue(recordedRequest.path.orEmpty().contains("includeSeries=true"))
    }

    @Test
    fun `searchEpisode POSTs an EpisodeSearch command with the episode id and returns the command id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": 42, "status": "queued"}"""))

        val commandId = api.searchEpisode(episodeId = 10)

        assertEquals(42, commandId)
        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.path.orEmpty().endsWith("/api/v3/command"))
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"name\":\"EpisodeSearch\""))
        assertTrue(body.contains("\"episodeIds\":[10]"))
    }

    @Test
    fun `searchSeries POSTs a SeriesSearch command with the series id and returns the command id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": 43, "status": "queued"}"""))

        val commandId = api.searchSeries(seriesId = 11)

        assertEquals(43, commandId)
        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.path.orEmpty().endsWith("/api/v3/command"))
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"name\":\"SeriesSearch\""))
        assertTrue(body.contains("\"seriesId\":11"))
    }

    @Test
    fun `getCommandStatus decodes the command's id and status`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": 42, "status": "completed"}"""))

        val status = api.getCommandStatus(commandId = 42)

        assertEquals(42, status.id)
        assertEquals("completed", status.status)
        val recordedRequest = server.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertTrue(recordedRequest.path.orEmpty().endsWith("/api/v3/command/42"))
    }

    @Test
    fun `getEpisodeById decodes season, episode number, title and series title`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"seasonNumber": 3, "episodeNumber": 4, "title": "Tumbleton", "series": {"title": "House of the Dragon"}}"""
            )
        )

        val episode = api.getEpisodeById(episodeId = 4531)

        assertEquals(3, episode.seasonNumber)
        assertEquals(4, episode.episodeNumber)
        assertEquals("Tumbleton", episode.title)
        assertEquals("House of the Dragon", episode.series?.title)
        val recordedRequest = server.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertTrue(recordedRequest.path.orEmpty().endsWith("/api/v3/episode/4531"))
    }

    @Test
    fun `getReleases decodes candidate releases and requests episodeId`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                    {
                        "guid": "abc123",
                        "indexerId": 4,
                        "indexer": "Some Indexer",
                        "title": "Show.S01E01.1080p.WEB-DL",
                        "size": 1500000000,
                        "seeders": 42,
                        "quality": {"quality": {"name": "WEBDL-1080p"}},
                        "rejected": false
                    }
                ]
                """
                    .trimIndent()
            )
        )

        val releases = api.getReleases(episodeId = 10, readTimeoutMs = 180_000L)

        assertEquals(1, releases.size)
        assertEquals("abc123", releases[0].guid)
        assertEquals(4, releases[0].indexerId)
        assertEquals("WEBDL-1080p", releases[0].quality?.quality?.name)

        val recordedRequest = server.takeRequest()
        assertTrue(recordedRequest.path.orEmpty().contains("episodeId=10"))
    }

    @Test
    fun `grabRelease POSTs the release's guid and indexerId`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        api.grabRelease(guid = "abc123", indexerId = 4)

        val recordedRequest = server.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.path.orEmpty().endsWith("/api/v3/release"))
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"guid\":\"abc123\""))
        assertTrue(body.contains("\"indexerId\":4"))
    }
}
