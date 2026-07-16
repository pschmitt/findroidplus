package dev.jdtech.jellyfin.api.pvr

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
}
