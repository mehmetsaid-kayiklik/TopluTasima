package com.example.toplutasima.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class AppDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migration 7 to 8 attributes legacy rows to current user`() = runBlocking {
        createVersion7Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.migration7To8("user-A"))
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase

        assertEquals(listOf("legacy-trip"), database.tripDao().getAllTrips("user-A").map { it.id })
        assertEquals(emptyList<String>(), database.tripDao().getAllTrips("user-B").map { it.id })
        assertEquals(
            listOf("legacy-profile"),
            database.profileDao().getAllProfiles("user-A").map { it.id }
        )
        assertEquals(
            listOf("legacy-link"),
            database.tripProfileLinkDao().getAllLinks("user-A").map { it.id }
        )
        database.close()
    }

    private fun createVersion7Database() {
        val db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("""
            CREATE TABLE `trips` (
                `id` TEXT NOT NULL PRIMARY KEY,
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
                `seatmateUuid` TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX `index_trips_yearMonth` ON `trips` (`yearMonth`)")
        db.execSQL("CREATE INDEX `index_trips_sortDate` ON `trips` (`sortDate`)")
        db.execSQL("CREATE INDEX `index_trips_firestoreDocId` ON `trips` (`firestoreDocId`)")
        db.execSQL("""
            CREATE TABLE `profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `displayName` TEXT NOT NULL,
                `memoryNote` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL,
                `sharedWithTransit` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE `trip_profile_links` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `tripStableKey` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `seatmateNote` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX `index_trip_profile_links_tripStableKey` ON `trip_profile_links` (`tripStableKey`)")
        db.execSQL("CREATE INDEX `index_trip_profile_links_profileId` ON `trip_profile_links` (`profileId`)")
        db.execSQL("INSERT INTO `trips` (`id`, `tarih`, `seatmateUuid`) VALUES ('legacy-trip', '01.07.2026', '')")
        db.execSQL("""
            INSERT INTO `profiles` (
                `id`, `displayName`, `createdAt`, `updatedAt`, `archived`, `sharedWithTransit`
            ) VALUES ('legacy-profile', 'Legacy', 1, 1, 0, 0)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `trip_profile_links` (
                `id`, `tripStableKey`, `profileId`, `createdAt`, `updatedAt`
            ) VALUES ('legacy-link', 'legacy-trip', 'legacy-profile', 1, 1)
        """.trimIndent())
        db.version = 7
        db.close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-7-8-test.db"
    }
}
