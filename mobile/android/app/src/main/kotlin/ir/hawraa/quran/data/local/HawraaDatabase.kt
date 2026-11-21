package ir.hawraa.quran.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReadingSessionEntity::class,
        PresetEntity::class,
        PresetSessionEntity::class,
        PresetProgressEntity::class,
        DownloadEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HawraaDatabase : RoomDatabase() {
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun presetDao(): PresetDao
    abstract fun presetSessionDao(): PresetSessionDao
    abstract fun presetProgressDao(): PresetProgressDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "hawraa.db"

        /**
         * v1 → v2 adds the four recitation tables and touches nothing else.
         *
         * Written by hand rather than as a destructive fallback: `reading_sessions` is the
         * user's reading history, and a version bump that wiped it would be the one data loss
         * this app cannot apologise for. The statements below are the *exact* SQL Room
         * generates for these entities (checked against `app/schemas/…/2.json`), because a
         * mismatch is not a compile error — it is a runtime `IllegalStateException` on the
         * user's device, on the upgrade, which is the worst possible moment.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `presets` (
                        `clientId` TEXT NOT NULL,
                        `serverId` TEXT,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `stepsJson` TEXT NOT NULL,
                        `reciterKey` TEXT,
                        `autoAdvance` INTEGER NOT NULL,
                        `ayahGapMs` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL DEFAULT 'pending',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`clientId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `preset_sessions` (
                        `clientId` TEXT NOT NULL,
                        `serverId` TEXT,
                        `presetClientId` TEXT,
                        `presetName` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `activeSec` INTEGER NOT NULL,
                        `fromSurah` INTEGER NOT NULL,
                        `fromAyah` INTEGER NOT NULL,
                        `toSurah` INTEGER NOT NULL,
                        `toAyah` INTEGER NOT NULL,
                        `fromPage` INTEGER,
                        `toPage` INTEGER,
                        `ayahsCompleted` INTEGER NOT NULL,
                        `stepsCompleted` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `pauseCount` INTEGER NOT NULL,
                        `seekCount` INTEGER NOT NULL,
                        `lastEventAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `reciterKey` TEXT,
                        `speed` REAL NOT NULL,
                        `eventsJson` TEXT,
                        `syncStatus` TEXT NOT NULL DEFAULT 'pending',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`clientId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `preset_ayah_progress` (
                        `presetClientId` TEXT NOT NULL,
                        `globalAyah` INTEGER NOT NULL,
                        `surah` INTEGER NOT NULL,
                        `ayah` INTEGER NOT NULL,
                        `page` INTEGER,
                        `plays` INTEGER NOT NULL,
                        `listenedSec` INTEGER NOT NULL,
                        `firstAt` INTEGER NOT NULL,
                        `lastAt` INTEGER NOT NULL,
                        `lastKind` TEXT,
                        `syncStatus` TEXT NOT NULL DEFAULT 'pending',
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`presetClientId`, `globalAyah`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_preset_ayah_progress_presetClientId` ON `preset_ayah_progress` (`presetClientId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_preset_ayah_progress_syncStatus` ON `preset_ayah_progress` (`syncStatus`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `editionKey` TEXT NOT NULL,
                        `reciterKey` TEXT NOT NULL,
                        `fileKey` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `scopeKey` TEXT NOT NULL,
                        `surah` INTEGER,
                        `ayah` INTEGER,
                        `juz` INTEGER,
                        `url` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `bytes` INTEGER NOT NULL,
                        `state` TEXT NOT NULL DEFAULT 'queued',
                        `error` TEXT,
                        `requestedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`editionKey`, `fileKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_state` ON `downloads` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_scopeKey` ON `downloads` (`scopeKey`)")
            }
        }

        /**
         * v2 → v3 adds the size the server said a file would be.
         *
         * Why it earns a column: when the server hosts the audio itself, its responses are
         * chunked and carry no `Content-Length`, so "the stream ended" is the only signal that
         * a download finished — and a dropped connection produces exactly that signal. Without
         * a declared size, a truncated file is stored as complete and plays until it stops.
         * With it, `fetchOne` compares what landed against what was promised and fails the row
         * instead.
         *
         * Nullable on purpose: files fetched from the upstream CDN have no known size in the
         * manifest, and `NULL` means "nothing to check against" rather than "expect zero".
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `expectedBytes` INTEGER")
            }
        }
    }
}
