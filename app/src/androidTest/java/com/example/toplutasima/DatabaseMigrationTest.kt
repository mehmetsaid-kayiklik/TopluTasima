package com.example.toplutasima

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @Test
    fun migration3To4_preservesTripsAndAddsSeatmateUuid() {
        runMigration(
            databaseName = "migration_3_4.db",
            startVersion = 3,
            endVersion = 4,
            migration = AppDatabase.MIGRATION_3_4,
            createAndSeed = { db ->
                createTripsTable(db, includeSeatmateUuid = false)
                insertTrip(db, includeSeatmateUuid = false)
            }
        ) { db ->
            db.query("SELECT hat, seatmateUuid FROM trips WHERE id = ?", arrayOf(TRIP_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("S8", cursor.getString(cursor.getColumnIndexOrThrow("hat")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("seatmateUuid")))
            }
        }
    }

    @Test
    fun migration4To5_preservesTripsAndCreatesHistoricalProfileSchema() {
        runMigration(
            databaseName = "migration_4_5.db",
            startVersion = 4,
            endVersion = 5,
            migration = AppDatabase.MIGRATION_4_5,
            createAndSeed = { db ->
                createTripsTable(db, includeSeatmateUuid = true)
                insertTrip(db, includeSeatmateUuid = true)
            }
        ) { db ->
            assertTripSurvived(db)
            val profileColumns = tableColumns(db, "profiles")
            assertTrue(
                profileColumns.containsAll(
                    setOf(
                        "id",
                        "displayName",
                        "nameKind",
                        "memoryNote",
                        "birthHint",
                        "infoSource",
                        "createdAt",
                        "updatedAt",
                        "archived"
                    )
                )
            )
            assertTrue(tableExists(db, "trip_profile_links"))
        }
    }

    @Test
    fun migration5To6_preservesTripsProfilesAndLinks() {
        runMigration(
            databaseName = "migration_5_6.db",
            startVersion = 5,
            endVersion = 6,
            migration = AppDatabase.MIGRATION_5_6,
            createAndSeed = { db ->
                createTripsTable(db, includeSeatmateUuid = true)
                createProfilesV5Table(db, includeSharedWithTransit = false)
                createTripProfileLinksTable(db)
                seedAllTables(db, includeLegacyProfileFields = true)
            }
        ) { db ->
            assertTripSurvived(db)
            db.query(
                "SELECT displayName, nameKind, infoSource, sharedWithTransit FROM profiles WHERE id = ?",
                arrayOf(PROFILE_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(cursor.getColumnIndexOrThrow("displayName")))
                assertEquals("NICKNAME", cursor.getString(cursor.getColumnIndexOrThrow("nameKind")))
                assertEquals("MANUAL", cursor.getString(cursor.getColumnIndexOrThrow("infoSource")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("sharedWithTransit")))
            }
            assertLinkSurvived(db)
        }
    }

    @Test
    fun migration6To7_preservesSupportedProfileAndLinkData() {
        runMigration(
            databaseName = "migration_6_7.db",
            startVersion = 6,
            endVersion = 7,
            migration = AppDatabase.MIGRATION_6_7,
            createAndSeed = { db ->
                createTripsTable(db, includeSeatmateUuid = true)
                createProfilesV5Table(db, includeSharedWithTransit = true)
                createTripProfileLinksTable(db)
                seedAllTables(db, includeLegacyProfileFields = true, sharedWithTransit = true)
            }
        ) { db ->
            assertTripSurvived(db)
            db.query(
                "SELECT displayName, memoryNote, sharedWithTransit FROM profiles WHERE id = ?",
                arrayOf(PROFILE_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(cursor.getColumnIndexOrThrow("displayName")))
                assertEquals("Window seat", cursor.getString(cursor.getColumnIndexOrThrow("memoryNote")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("sharedWithTransit")))
            }
            assertFalse(tableColumns(db, "profiles").contains("nameKind"))
            assertLinkSurvived(db)
        }
    }

    @Test
    fun migration7To8_preservesDataAndAttributesItToCurrentUser() {
        runMigration(
            databaseName = "migration_7_8.db",
            startVersion = 7,
            endVersion = 8,
            migration = AppDatabase.migration7To8(USER_ID),
            createAndSeed = { db ->
                createTripsTable(db, includeSeatmateUuid = true)
                createProfilesV7Table(db)
                createTripProfileLinksTable(db)
                seedAllTables(db, includeLegacyProfileFields = false, sharedWithTransit = true)
            }
        ) { db ->
            db.query("SELECT hat, seatmateUuid, userId FROM trips WHERE id = ?", arrayOf(TRIP_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("S8", cursor.getString(cursor.getColumnIndexOrThrow("hat")))
                assertEquals(SEATMATE_UUID, cursor.getString(cursor.getColumnIndexOrThrow("seatmateUuid")))
                assertEquals(USER_ID, cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            }
            db.query("SELECT displayName, userId FROM profiles WHERE id = ?", arrayOf(PROFILE_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Ada", cursor.getString(cursor.getColumnIndexOrThrow("displayName")))
                assertEquals(USER_ID, cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            }
            db.query("SELECT seatmateNote, userId FROM trip_profile_links WHERE id = ?", arrayOf(LINK_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Met on S8", cursor.getString(cursor.getColumnIndexOrThrow("seatmateNote")))
                assertEquals(USER_ID, cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            }
        }
    }

    private fun runMigration(
        databaseName: String,
        startVersion: Int,
        endVersion: Int,
        migration: Migration,
        createAndSeed: (SQLiteDatabase) -> Unit,
        verify: (SupportSQLiteDatabase) -> Unit
    ) {
        val context = TestDatabaseFactory.targetContext()
        val dbFile = context.getDatabasePath(databaseName)
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createAndSeed(db)
            db.version = startVersion
        }

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(endVersion) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        check(oldVersion == startVersion && newVersion == endVersion) {
                            "Unexpected migration $oldVersion -> $newVersion"
                        }
                        migration.migrate(db)
                    }
                })
                .build()
        )

        try {
            verify(openHelper.writableDatabase)
        } finally {
            openHelper.close()
            dbFile.delete()
        }
    }

    private fun createTripsTable(db: SQLiteDatabase, includeSeatmateUuid: Boolean) {
        val seatmateColumn = if (includeSeatmateUuid) {
            ", `seatmateUuid` TEXT NOT NULL DEFAULT ''"
        } else {
            ""
        }
        db.execSQL(
            """
            CREATE TABLE `trips` (
                `id` TEXT NOT NULL,
                `firestoreDocId` TEXT,
                `tarih` TEXT,
                `gun` TEXT,
                `tur` TEXT,
                `hat` TEXT,
                `yon` TEXT,
                `binisDuragi` TEXT,
                `planlananBinis` TEXT,
                `gercekBinis` TEXT,
                `gecikme` TEXT,
                `inisDuragi` TEXT,
                `planlananInis` TEXT,
                `gercekInis` TEXT,
                `gununTipi` TEXT,
                `havaDurumu` TEXT,
                `oturabildimMi` TEXT,
                `planlananYolSuresi` TEXT,
                `gercekYolSuresi` TEXT,
                `not` TEXT,
                `biletKontrolu` TEXT,
                `mesafe` TEXT,
                `orsMesafeKm` REAL,
                `orsMesafeText` TEXT,
                `rmvMesafeKm` REAL,
                `rmvMesafeMetre` INTEGER,
                `rmvMesafeText` TEXT,
                `rmvMesafeDurumu` TEXT,
                `rmvMesafeGuncellemeTarihi` TEXT,
                `rmvApiVersion` TEXT,
                `journeyRef` TEXT,
                `fromStopId` TEXT,
                `toStopId` TEXT,
                `durakSayisi` TEXT,
                `yearMonth` TEXT,
                `sortDate` TEXT
                $seatmateColumn,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_trips_yearMonth` ON `trips` (`yearMonth`)")
        db.execSQL("CREATE INDEX `index_trips_sortDate` ON `trips` (`sortDate`)")
        db.execSQL("CREATE INDEX `index_trips_firestoreDocId` ON `trips` (`firestoreDocId`)")
    }

    private fun createProfilesV5Table(
        db: SQLiteDatabase,
        includeSharedWithTransit: Boolean
    ) {
        val sharedColumn = if (includeSharedWithTransit) {
            ", `sharedWithTransit` INTEGER NOT NULL DEFAULT 0"
        } else {
            ""
        }
        db.execSQL(
            """
            CREATE TABLE `profiles` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `nameKind` TEXT NOT NULL,
                `memoryNote` TEXT,
                `birthHint` TEXT,
                `infoSource` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL
                $sharedColumn,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }

    private fun createProfilesV7Table(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `profiles` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `memoryNote` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL,
                `sharedWithTransit` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }

    private fun createTripProfileLinksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `trip_profile_links` (
                `id` TEXT NOT NULL,
                `tripStableKey` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `seatmateNote` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_trip_profile_links_tripStableKey` ON `trip_profile_links` (`tripStableKey`)")
        db.execSQL("CREATE INDEX `index_trip_profile_links_profileId` ON `trip_profile_links` (`profileId`)")
    }

    private fun seedAllTables(
        db: SQLiteDatabase,
        includeLegacyProfileFields: Boolean,
        sharedWithTransit: Boolean = false
    ) {
        insertTrip(db, includeSeatmateUuid = true)
        val profileValues = ContentValues().apply {
            put("id", PROFILE_ID)
            put("displayName", "Ada")
            put("memoryNote", "Window seat")
            put("createdAt", 100L)
            put("updatedAt", 200L)
            put("archived", 0)
            put("sharedWithTransit", if (sharedWithTransit) 1 else 0)
            if (includeLegacyProfileFields) {
                put("nameKind", "NICKNAME")
                put("birthHint", "Spring")
                put("infoSource", "MANUAL")
            }
        }
        if (!tableColumns(db, "profiles").contains("sharedWithTransit")) {
            profileValues.remove("sharedWithTransit")
        }
        db.insertOrThrow("profiles", null, profileValues)

        db.insertOrThrow(
            "trip_profile_links",
            null,
            ContentValues().apply {
                put("id", LINK_ID)
                put("tripStableKey", TRIP_ID)
                put("profileId", PROFILE_ID)
                put("seatmateNote", "Met on S8")
                put("createdAt", 300L)
                put("updatedAt", 400L)
            }
        )
    }

    private fun insertTrip(db: SQLiteDatabase, includeSeatmateUuid: Boolean) {
        db.insertOrThrow(
            "trips",
            null,
            ContentValues().apply {
                put("id", TRIP_ID)
                put("firestoreDocId", "doc-1")
                put("hat", "S8")
                put("yearMonth", "2026-07")
                put("sortDate", "2026-07-15")
                if (includeSeatmateUuid) put("seatmateUuid", SEATMATE_UUID)
            }
        )
    }

    private fun assertTripSurvived(db: SupportSQLiteDatabase) {
        db.query("SELECT hat, seatmateUuid FROM trips WHERE id = ?", arrayOf(TRIP_ID)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("S8", cursor.getString(cursor.getColumnIndexOrThrow("hat")))
            assertEquals(SEATMATE_UUID, cursor.getString(cursor.getColumnIndexOrThrow("seatmateUuid")))
        }
    }

    private fun assertLinkSurvived(db: SupportSQLiteDatabase) {
        db.query("SELECT profileId, seatmateNote FROM trip_profile_links WHERE id = ?", arrayOf(LINK_ID)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(PROFILE_ID, cursor.getString(cursor.getColumnIndexOrThrow("profileId")))
            assertEquals("Met on S8", cursor.getString(cursor.getColumnIndexOrThrow("seatmateNote")))
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { it.moveToFirst() }

    private fun tableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> =
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun tableColumns(db: SQLiteDatabase, tableName: String): Set<String> =
        db.rawQuery("PRAGMA table_info(`$tableName`)", null).use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private companion object {
        const val TRIP_ID = "trip-1"
        const val PROFILE_ID = "profile-1"
        const val LINK_ID = "link-1"
        const val SEATMATE_UUID = "sm-uuid-999"
        const val USER_ID = "migration-user"
    }
}
