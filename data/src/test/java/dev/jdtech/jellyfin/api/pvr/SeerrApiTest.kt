package dev.jdtech.jellyfin.api.pvr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeerrApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SeerrApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = SeerrApi(baseUrl = server.url("/").toString(), apiKey = "test-api-key")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getTvSeason returns episode details from the season endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "id": 1,
                    "name": "Season 2",
                    "seasonNumber": 2,
                    "episodes": [
                        {
                            "id": 33,
                            "name": "A New Episode",
                            "seasonNumber": 2,
                            "episodeNumber": 4,
                            "airDate": "2026-08-01",
                            "overview": "Episode overview",
                            "stillPath": "/still.jpg"
                        }
                    ]
                }
                """
                    .trimIndent()
            )
        )

        val season = api.getTvSeason(tmdbId = 123, seasonNumber = 2)

        assertEquals(2, season.seasonNumber)
        assertEquals(1, season.episodes.size)
        assertEquals("A New Episode", season.episodes.single().name)
        assertEquals(4, season.episodes.single().episodeNumber)
        val request = server.takeRequest()
        assertEquals("test-api-key", request.getHeader("X-Api-Key"))
        assertTrue(request.path.orEmpty().endsWith("/api/v1/tv/123/season/2"))
    }

    @Test
    fun `getTvDetails parses per-season media info`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "id": 1,
                    "name": "Some Show",
                    "numberOfSeasons": 3,
                    "mediaInfo": {
                        "status": 4,
                        "requests": [],
                        "seasons": [
                            { "seasonNumber": 1, "status": 5 },
                            { "seasonNumber": 2, "status": 2 }
                        ]
                    }
                }
                """
                    .trimIndent()
            )
        )

        val details = api.getTvDetails(tmdbId = 1)

        assertEquals(3, details.numberOfSeasons)
        assertEquals(4, details.mediaInfo?.status)
        val seasons = details.mediaInfo?.seasons.orEmpty()
        assertEquals(2, seasons.size)
        assertEquals(1, seasons[0].seasonNumber)
        assertEquals(5, seasons[0].status)
        assertEquals(2, seasons[1].seasonNumber)
        assertEquals(2, seasons[1].status)
        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().endsWith("/api/v1/tv/1"))
    }

    @Test
    fun `getTvDetails defaults seasons to empty when mediaInfo is absent`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                    "id": 1,
                    "name": "Never Requested Show",
                    "numberOfSeasons": 2
                }
                """
                    .trimIndent()
            )
        )

        val details = api.getTvDetails(tmdbId = 1)

        assertEquals(null, details.mediaInfo)
    }

    @Test
    fun `createRequest without a season number requests all seasons`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        api.createRequest(mediaType = SeerrApi.MEDIA_TYPE_TV, tmdbId = 123)

        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().endsWith("/api/v1/request"))
        assertEquals(
            """{"mediaType":"tv","mediaId":123,"seasons":"all"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `createRequest with a season number requests only that season`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        api.createRequest(mediaType = SeerrApi.MEDIA_TYPE_TV, tmdbId = 123, seasonNumber = 2)

        val request = server.takeRequest()
        assertEquals(
            """{"mediaType":"tv","mediaId":123,"seasons":[2]}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `createRequest for a movie ignores season number`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        api.createRequest(mediaType = SeerrApi.MEDIA_TYPE_MOVIE, tmdbId = 456, seasonNumber = 2)

        val request = server.takeRequest()
        assertEquals(
            """{"mediaType":"movie","mediaId":456}""",
            request.body.readUtf8(),
        )
    }
}
