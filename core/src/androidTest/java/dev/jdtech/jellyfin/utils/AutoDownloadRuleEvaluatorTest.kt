package dev.jdtech.jellyfin.utils

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.database.ServerDatabase
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoDownloadRuleEvaluatorTest {
    private lateinit var db: ServerDatabase
    private lateinit var dao: ServerDatabaseDao
    private val evaluator = AutoDownloadRuleEvaluator()

    private val seriesId = UUID.randomUUID()
    private val serverId = "server-a"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ServerDatabase::class.java).build()
        dao = db.getServerDatabaseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun source(id: String) =
        FindroidSource(
            id = id,
            name = id,
            type = FindroidSourceType.REMOTE,
            path = "https://example.com/$id",
            size = 0L,
            mediaStreams = emptyList(),
        )

    private fun rule(seasonId: UUID?, enabled: Boolean = true) =
        AutoDownloadRuleDto(
            serverId = serverId,
            userId = UUID.randomUUID(),
            seriesId = seriesId,
            seasonId = seasonId,
            enabled = enabled,
            createdAt = 0L,
        )

    @Test
    fun showRule_queuesEpisodesFromAllSeasons() = runBlocking {
        val season1 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val season2 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val episode1 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season1.id,
                seriesId = seriesId,
                sources = listOf(source("s1")),
            )
        val episode2 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season2.id,
                seriesId = seriesId,
                sources = listOf(source("s2")),
            )
        val repository =
            FakeJellyfinRepository(
                seasons = listOf(season1, season2),
                episodesBySeasonId = mapOf(season1.id to listOf(episode1), season2.id to listOf(episode2)),
            )
        val downloader = FakeDownloader()

        evaluator.evaluate(rule(seasonId = null), dao, repository, downloader)

        assertTrue(downloader.downloadedItemIds.contains(episode1.id.toString()))
        assertTrue(downloader.downloadedItemIds.contains(episode2.id.toString()))
    }

    @Test
    fun seasonRule_scopesToSingleSeason() = runBlocking {
        val season1 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val season2 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val episode1 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season1.id,
                seriesId = seriesId,
                sources = listOf(source("s1")),
            )
        val episode2 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season2.id,
                seriesId = seriesId,
                sources = listOf(source("s2")),
            )
        val repository =
            FakeJellyfinRepository(
                seasons = listOf(season1, season2),
                episodesBySeasonId = mapOf(season1.id to listOf(episode1), season2.id to listOf(episode2)),
            )
        val downloader = FakeDownloader()

        evaluator.evaluate(rule(seasonId = season1.id), dao, repository, downloader)

        assertTrue(downloader.downloadedItemIds.contains(episode1.id.toString()))
        assertFalse(downloader.downloadedItemIds.contains(episode2.id.toString()))
    }

    @Test
    fun dedup_skipsEpisodeThatAlreadyHasASourceRow() = runBlocking {
        val season1 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val episode1 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season1.id,
                seriesId = seriesId,
                sources = listOf(source("s1")),
            )
        val episode2 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season1.id,
                seriesId = seriesId,
                sources = listOf(source("s2")),
            )
        // episode1 already has a sources row - covers downloaded, queued, and running alike,
        // since all three states are represented the same way in this codebase (see
        // AutoDownloadRuleEvaluator's doc comment).
        dao.insertSource(
            FindroidSourceDto(
                id = "existing",
                itemId = episode1.id,
                name = "existing",
                type = FindroidSourceType.LOCAL,
                path = "/tmp/existing",
            )
        )
        val repository =
            FakeJellyfinRepository(
                seasons = listOf(season1),
                episodesBySeasonId = mapOf(season1.id to listOf(episode1, episode2)),
            )
        val downloader = FakeDownloader()

        evaluator.evaluate(rule(seasonId = null), dao, repository, downloader)

        assertFalse(downloader.downloadedItemIds.contains(episode1.id.toString()))
        assertTrue(downloader.downloadedItemIds.contains(episode2.id.toString()))
    }

    @Test
    fun disabledRule_doesNotQueueAnything() = runBlocking {
        val season1 = dummySeason.copy(id = UUID.randomUUID(), seriesId = seriesId)
        val episode1 =
            dummyEpisode.copy(
                id = UUID.randomUUID(),
                seasonId = season1.id,
                seriesId = seriesId,
                sources = listOf(source("s1")),
            )
        val repository =
            FakeJellyfinRepository(
                seasons = listOf(season1),
                episodesBySeasonId = mapOf(season1.id to listOf(episode1)),
            )
        val downloader = FakeDownloader()

        evaluator.evaluate(rule(seasonId = null, enabled = false), dao, repository, downloader)

        assertTrue(downloader.downloadedItemIds.isEmpty())
    }
}
