package dev.pschmitt.jellyfin.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.pschmitt.jellyfin.models.AutoDownloadRuleDto
import dev.pschmitt.jellyfin.models.JollyfinEpisodeDto
import dev.pschmitt.jellyfin.models.JollyfinMediaStreamDto
import dev.pschmitt.jellyfin.models.JollyfinMovieDto
import dev.pschmitt.jellyfin.models.JollyfinSeasonDto
import dev.pschmitt.jellyfin.models.JollyfinSegmentDto
import dev.pschmitt.jellyfin.models.JollyfinShowDto
import dev.pschmitt.jellyfin.models.JollyfinSourceDto
import dev.pschmitt.jellyfin.models.JollyfinTrickplayInfoDto
import dev.pschmitt.jellyfin.models.JollyfinUserDataDto
import dev.pschmitt.jellyfin.models.PendingDownloadRequestDto
import dev.pschmitt.jellyfin.models.Profile
import dev.pschmitt.jellyfin.models.PvrServiceConfig
import dev.pschmitt.jellyfin.models.Server
import dev.pschmitt.jellyfin.models.ServerAddress
import dev.pschmitt.jellyfin.models.User

@Database(
    entities =
        [
            Server::class,
            ServerAddress::class,
            User::class,
            JollyfinMovieDto::class,
            JollyfinShowDto::class,
            JollyfinSeasonDto::class,
            JollyfinEpisodeDto::class,
            JollyfinSourceDto::class,
            JollyfinMediaStreamDto::class,
            JollyfinUserDataDto::class,
            JollyfinTrickplayInfoDto::class,
            JollyfinSegmentDto::class,
            AutoDownloadRuleDto::class,
            PendingDownloadRequestDto::class,
            Profile::class,
            PvrServiceConfig::class,
        ],
    version = 17,
    autoMigrations =
        [
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5, spec = ServerDatabase.TrickplayMigration::class),
            AutoMigration(from = 5, to = 6, spec = ServerDatabase.IntrosMigration::class),
            AutoMigration(from = 7, to = 8),
            AutoMigration(from = 8, to = 9),
            AutoMigration(from = 9, to = 10),
            AutoMigration(from = 10, to = 11),
            AutoMigration(from = 11, to = 12),
            AutoMigration(from = 12, to = 13),
            AutoMigration(from = 13, to = 14),
            AutoMigration(from = 14, to = 15),
            AutoMigration(from = 15, to = 16),
        ],
)
@TypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun getServerDatabaseDao(): ServerDatabaseDao

    @DeleteTable(tableName = "trickPlayManifests") class TrickplayMigration : AutoMigrationSpec

    @DeleteTable(tableName = "intros") class IntrosMigration : AutoMigrationSpec
}

val MIGRATION_6_7 =
    object : Migration(startVersion = 6, endVersion = 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE segments")
            db.execSQL(
                "CREATE TABLE segments (`itemId` TEXT NOT NULL, `type` TEXT NOT NULL, `startTicks` INTEGER NOT NULL, `endTicks` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `type`), FOREIGN KEY(`itemId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }

/**
 * Adds the `profiles`/`pvrServiceConfigs` tables backing the Profiles feature. Data backfill
 * (creating a Profile per existing User, migrating global PVR prefs into PvrServiceConfig rows)
 * happens separately at app startup via ProfileMigrationRunner, since migrate() only has access to
 * raw SQL and not SharedPreferences/EncryptedSharedPreferences.
 */
val MIGRATION_16_17 =
    object : Migration(startVersion = 16, endVersion = 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pvrServiceConfigs` (`id` TEXT NOT NULL, `service` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `baseUrl` TEXT, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `userId` TEXT NOT NULL, `isMain` INTEGER NOT NULL, `sonarrConfigId` TEXT, `radarrConfigId` TEXT, `seerrConfigId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`sonarrConfigId`) REFERENCES `pvrServiceConfigs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`radarrConfigId`) REFERENCES `pvrServiceConfigs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`seerrConfigId`) REFERENCES `pvrServiceConfigs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_profiles_userId` ON `profiles` (`userId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_profiles_sonarrConfigId` ON `profiles` (`sonarrConfigId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_profiles_radarrConfigId` ON `profiles` (`radarrConfigId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_profiles_seerrConfigId` ON `profiles` (`seerrConfigId`)"
            )
        }
    }
