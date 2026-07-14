package dev.jdtech.jellyfin.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidSeasonDto
import dev.jdtech.jellyfin.models.FindroidShowDto
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.User
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoDownloadRuleDaoTest {
    private lateinit var db: ServerDatabase
    private lateinit var dao: ServerDatabaseDao

    private val serverA = "server-a"
    private val serverB = "server-b"
    private val userA = UUID.randomUUID()
    private val userB = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ServerDatabase::class.java).build()
        dao = db.getServerDatabaseDao()

        dao.insertServer(Server(id = serverA, name = serverA, currentServerAddressId = null, currentUserId = null))
        dao.insertServer(Server(id = serverB, name = serverB, currentServerAddressId = null, currentUserId = null))
        dao.insertUser(User(id = userA, name = "userA", serverId = serverA))
        dao.insertUser(User(id = userB, name = "userB", serverId = serverA))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rule(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonId: UUID? = null,
        enabled: Boolean = true,
    ) =
        AutoDownloadRuleDto(
            serverId = serverId,
            userId = userId,
            seriesId = seriesId,
            seasonId = seasonId,
            enabled = enabled,
            createdAt = System.currentTimeMillis(),
        )

    @Test
    fun rulesAreScopedByServerAndUser() {
        val seriesId = UUID.randomUUID()
        dao.insertAutoDownloadRule(rule(serverA, userA, seriesId))
        dao.insertAutoDownloadRule(rule(serverB, userA, seriesId))
        dao.insertAutoDownloadRule(rule(serverA, userB, seriesId))

        val rulesForServerAUserA = dao.getAutoDownloadRules(serverA, userA)

        assertEquals(1, rulesForServerAUserA.size)
        assertEquals(serverA, rulesForServerAUserA[0].serverId)
        assertEquals(userA, rulesForServerAUserA[0].userId)
    }

    @Test
    fun enabledRulesQueryExcludesDisabledRules() {
        val seriesId1 = UUID.randomUUID()
        val seriesId2 = UUID.randomUUID()
        dao.insertAutoDownloadRule(rule(serverA, userA, seriesId1, enabled = true))
        dao.insertAutoDownloadRule(rule(serverA, userA, seriesId2, enabled = false))

        val enabled = dao.getEnabledAutoDownloadRules(serverA, userA)

        assertEquals(1, enabled.size)
        assertEquals(seriesId1, enabled[0].seriesId)
    }

    @Test
    fun showRuleAndSeasonRuleForSameShowCoexist() {
        val seriesId = UUID.randomUUID()
        val seasonId = UUID.randomUUID()
        dao.insertAutoDownloadRule(rule(serverA, userA, seriesId, seasonId = null))
        dao.insertAutoDownloadRule(rule(serverA, userA, seriesId, seasonId = seasonId))

        assertEquals(2, dao.getAutoDownloadRules(serverA, userA).size)
        assertTrue(dao.getShowAutoDownloadRule(serverA, userA, seriesId) != null)
        assertTrue(dao.getSeasonAutoDownloadRule(serverA, userA, seriesId, seasonId) != null)
    }

    private fun insertShowAndSeason(seriesId: UUID, seasonId: UUID) {
        dao.insertShow(
            FindroidShowDto(
                id = seriesId,
                serverId = serverA,
                name = "Show",
                originalTitle = null,
                overview = "",
                runtimeTicks = 0L,
                communityRating = null,
                officialRating = null,
                status = "Continuing",
                productionYear = null,
                endDate = null,
            )
        )
        dao.insertSeason(
            FindroidSeasonDto(
                id = seasonId,
                seriesId = seriesId,
                name = "Season 1",
                seriesName = "Show",
                overview = "",
                indexNumber = 1,
            )
        )
    }

    @Test
    fun disablingRuleDoesNotDeleteExistingDownloads() {
        val itemId = UUID.randomUUID()
        val seasonId = UUID.randomUUID()
        val seriesId = UUID.randomUUID()
        insertShowAndSeason(seriesId, seasonId)
        dao.insertEpisode(
            FindroidEpisodeDto(
                id = itemId,
                serverId = serverA,
                seasonId = seasonId,
                seriesId = seriesId,
                name = "Episode",
                seriesName = "Show",
                overview = "",
                indexNumber = 1,
                indexNumberEnd = null,
                parentIndexNumber = 1,
                runtimeTicks = 0L,
                premiereDate = null,
                communityRating = null,
                chapters = null,
            )
        )
        dao.insertSource(
            FindroidSourceDto(
                id = "source-1",
                itemId = itemId,
                name = "source",
                type = FindroidSourceType.LOCAL,
                path = "/tmp/does-not-matter",
            )
        )

        val id = dao.insertAutoDownloadRule(rule(serverA, userA, seriesId))
        dao.setAutoDownloadRuleEnabled(id, false)

        assertEquals(false, dao.getAutoDownloadRules(serverA, userA)[0].enabled)
        assertEquals(itemId, dao.getEpisode(itemId).id)
        assertEquals(1, dao.getSources(itemId).size)
    }

    @Test
    fun deletingRuleDoesNotDeleteExistingDownloads() {
        val itemId = UUID.randomUUID()
        val seasonId = UUID.randomUUID()
        val seriesId = UUID.randomUUID()
        insertShowAndSeason(seriesId, seasonId)
        dao.insertEpisode(
            FindroidEpisodeDto(
                id = itemId,
                serverId = serverA,
                seasonId = seasonId,
                seriesId = seriesId,
                name = "Episode",
                seriesName = "Show",
                overview = "",
                indexNumber = 1,
                indexNumberEnd = null,
                parentIndexNumber = 1,
                runtimeTicks = 0L,
                premiereDate = null,
                communityRating = null,
                chapters = null,
            )
        )
        dao.insertSource(
            FindroidSourceDto(
                id = "source-1",
                itemId = itemId,
                name = "source",
                type = FindroidSourceType.LOCAL,
                path = "/tmp/does-not-matter",
            )
        )

        val id = dao.insertAutoDownloadRule(rule(serverA, userA, seriesId))
        dao.deleteAutoDownloadRule(id)

        assertNull(dao.getShowAutoDownloadRule(serverA, userA, seriesId))
        assertEquals(1, dao.getSources(itemId).size)
        assertEquals(itemId, dao.getEpisode(itemId).id)
    }
}
