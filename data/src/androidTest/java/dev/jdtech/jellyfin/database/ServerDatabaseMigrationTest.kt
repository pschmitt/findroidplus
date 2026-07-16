package dev.jdtech.jellyfin.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerDatabaseMigrationTest {
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ServerDatabase::class.java)

    @Test
    fun migrate8To9_createsAutoDownloadRulesTable() {
        helper.createDatabase(testDbName, 8).apply { close() }

        val db = helper.runMigrationsAndValidate(testDbName, 9, true)

        val cursor =
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='autoDownloadRules'"
            )
        cursor.use { assert(it.count == 1) { "autoDownloadRules table should exist after migration" } }
        db.close()
    }

    @Test
    fun migrate9To10_addsOnlyNewEpisodesColumnWithDefault() {
        val v9 = helper.createDatabase(testDbName, 9)
        v9.execSQL(
            "INSERT INTO autoDownloadRules (serverId, userId, seriesId, seasonId, enabled, createdAt) VALUES ('s', 'u', 'series', NULL, 1, 0)"
        )
        v9.close()

        val db = helper.runMigrationsAndValidate(testDbName, 10, true)

        val cursor = db.query("SELECT onlyNewEpisodes FROM autoDownloadRules")
        cursor.use {
            assert(it.moveToFirst()) { "Pre-migration row should survive the migration" }
            assert(it.getInt(0) == 0) { "onlyNewEpisodes should default to false (0) for old rows" }
        }
        db.close()
    }

    @Test
    fun migrate12To13_addsTvdbIdAndTmdbIdColumnsWithNullDefault() {
        val v12 = helper.createDatabase(testDbName, 12)
        v12.execSQL(
            "INSERT INTO shows (id, serverId, name, originalTitle, overview, runtimeTicks, communityRating, officialRating, status, productionYear, endDate) VALUES ('show1', 's', 'Show', NULL, 'overview', 0, NULL, NULL, 'Ended', NULL, NULL)"
        )
        v12.execSQL(
            "INSERT INTO movies (id, serverId, name, originalTitle, overview, runtimeTicks, premiereDate, communityRating, officialRating, status, productionYear, endDate, chapters) VALUES ('movie1', 's', 'Movie', NULL, 'overview', 0, NULL, NULL, NULL, 'Ended', NULL, NULL, NULL)"
        )
        v12.close()

        val db = helper.runMigrationsAndValidate(testDbName, 13, true)

        val showCursor = db.query("SELECT tvdbId FROM shows WHERE id = 'show1'")
        showCursor.use {
            assert(it.moveToFirst()) { "Pre-migration show row should survive the migration" }
            assert(it.isNull(0)) { "tvdbId should default to NULL for old rows" }
        }

        val movieCursor = db.query("SELECT tmdbId FROM movies WHERE id = 'movie1'")
        movieCursor.use {
            assert(it.moveToFirst()) { "Pre-migration movie row should survive the migration" }
            assert(it.isNull(0)) { "tmdbId should default to NULL for old rows" }
        }
        db.close()
    }
}
