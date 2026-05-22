package com.example.toplutasima

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @Test
    fun testMigration4to5_PreservesTripsData() {
        val context = TestDatabaseFactory.targetContext()
        val dbFile = context.getDatabasePath("test_migration_db")
        dbFile.delete()

        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 4
        sqliteDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trips` (
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
                `sortDate` TEXT,
                `seatmateUuid` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        val values = ContentValues().apply {
            put("id", "trip-1")
            put("firestoreDocId", "doc-1")
            put("hat", "S8")
            put("seatmateUuid", "sm-uuid-999")
        }
        sqliteDb.insert("trips", null, values)
        sqliteDb.close()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration_db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        if (oldVersion == 4 && newVersion == 5) {
                            AppDatabase.MIGRATION_4_5.migrate(db)
                        }
                    }
                })
                .build()
        )

        val writableSupportDb = openHelper.writableDatabase

        val cursorProfiles =
            writableSupportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='profiles'")
        assertTrue(cursorProfiles.moveToFirst())
        cursorProfiles.close()

        val cursorLinks =
            writableSupportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='trip_profile_links'")
        assertTrue(cursorLinks.moveToFirst())
        cursorLinks.close()

        val cursorTrips = writableSupportDb.query("SELECT * FROM trips WHERE id='trip-1'")
        assertTrue(cursorTrips.moveToFirst())
        val hatIndex = cursorTrips.getColumnIndex("hat")
        val seatmateUuidIndex = cursorTrips.getColumnIndex("seatmateUuid")
        assertEquals("S8", cursorTrips.getString(hatIndex))
        assertEquals("sm-uuid-999", cursorTrips.getString(seatmateUuidIndex))
        cursorTrips.close()

        writableSupportDb.close()
        openHelper.close()
        dbFile.delete()
    }
}
