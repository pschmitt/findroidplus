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

class RadarrApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RadarrApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApi(baseUrl = server.url("/").toString(), apiKey = "test-api-key")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getMovie decodes only the fields we need and ignores the rest`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                    {
                        "id": 7,
                        "tmdbId": 98765,
                        "title": "Some Movie",
                        "someUnknownField": {"nested": true}
                    }
                ]
                """
                    .trimIndent()
            )
        )

        val movies = api.getMovie()

        assertEquals(1, movies.size)
        assertEquals(7, movies[0].id)
        assertEquals(98765, movies[0].tmdbId)
        assertEquals("Some Movie", movies[0].title)
    }

    @Test
    fun `getQueue unwraps the paginated records list, keyed by movieId, and requests a large page size`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                        "page": 1,
                        "pageSize": 250,
                        "totalRecords": 1,
                        "records": [
                            {
                                "id": 99,
                                "movieId": 7,
                                "status": "downloading",
                                "size": 2000,
                                "sizeleft": 500
                            }
                        ]
                    }
                    """
                        .trimIndent()
                )
            )

            val queue = api.getQueue()

            assertEquals(1, queue.size)
            assertEquals(99, queue[0].id)
            assertEquals(7, queue[0].movieId)
            assertEquals("downloading", queue[0].status)

            val recordedRequest = server.takeRequest()
            assertEquals("test-api-key", recordedRequest.getHeader("X-Api-Key"))
            assertTrue(recordedRequest.path.orEmpty().contains("pageSize=250"))
        }

    @Test
    fun `getCalendar decodes a flat array and requests start and end, with no includeSeries param`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    [
                        {
                            "id": 21,
                            "tmdbId": 98765,
                            "title": "Some Movie",
                            "hasFile": false,
                            "monitored": true,
                            "digitalRelease": "2024-07-24T00:00:00Z"
                        }
                    ]
                    """
                        .trimIndent()
                )
            )

            val entries = api.getCalendar(LocalDate.of(2024, 7, 21), LocalDate.of(2024, 8, 20))

            assertEquals(1, entries.size)
            assertEquals(21, entries[0].id)
            assertEquals(98765, entries[0].tmdbId)
            assertEquals("2024-07-24T00:00:00Z", entries[0].digitalRelease)

            val recordedRequest = server.takeRequest()
            assertEquals("test-api-key", recordedRequest.getHeader("X-Api-Key"))
            assertTrue(recordedRequest.path.orEmpty().contains("start=2024-07-21"))
            assertTrue(recordedRequest.path.orEmpty().contains("end=2024-08-20"))
            assertTrue(!recordedRequest.path.orEmpty().contains("includeSeries"))
        }
}
