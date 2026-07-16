package dev.jdtech.jellyfin.api.pvr

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
}
