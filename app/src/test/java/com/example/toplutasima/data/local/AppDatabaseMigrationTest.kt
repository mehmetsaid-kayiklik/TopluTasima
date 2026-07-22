package com.example.toplutasima.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    fun `migration 7 to 11 attributes legacy rows and preserves them`() = runBlocking {
        createVersion7Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                AppDatabase.migration7To8("user-A"),
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
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

    @Test
    fun `migration 8 to 11 creates drive tables without changing existing rows`() = runBlocking {
        createVersion8Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase

        assertEquals(listOf("legacy-trip"), database.tripDao().getAllTrips("user-A").map { it.id })
        assertEquals(
            listOf("legacy-profile"),
            database.profileDao().getAllProfiles("user-A").map { it.id }
        )
        assertEquals(
            listOf("legacy-link"),
            database.tripProfileLinkDao().getAllLinks("user-A").map { it.id }
        )

        database.driveVehicleDao().upsert(vehicle())
        database.driveTripDao().upsert(trip())
        database.driveSyncOperationDao().upsert(operation())

        assertEquals("Vehicle", database.driveVehicleDao().getVehicle("user-A", "vehicle-1")?.displayName)
        assertEquals("vehicle-1", database.driveTripDao().getTrip("user-A", "drive-trip-1")?.vehicleId)
        assertEquals(1, database.driveSyncOperationDao().pendingCount("user-A"))
        assertEquals(0, database.driveSyncOperationDao().pendingCount("user-B"))
        database.close()
    }

    @Test
    fun `migration 9 to 11 preserves drive rows and creates sync metadata`() = runBlocking {
        createVersion9Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase

        assertEquals("Vehicle", database.driveVehicleDao()
            .getVehicle("user-A", "vehicle-1")?.displayName)
        database.driveSyncMetadataDao().upsert(
            com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity(
                userId = "user-A",
                initialHydrationCompleted = true,
                updatedAt = 30L
            )
        )
        assertEquals(true, database.driveSyncMetadataDao().get("user-A")
            ?.initialHydrationCompleted)
        assertEquals(emptyList<Any>(), database.driveSyncReceiptDao()
            .observeRecent("user-A", 10).first())
        database.close()
    }

    private fun createVersion9Database() {
        createVersion8Database()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        check(oldVersion == 8 && newVersion == 9)
                        AppDatabase.MIGRATION_8_9.migrate(db)
                        db.execSQL(
                            "INSERT INTO drive_vehicles " +
                                "(id, userId, displayName, createdAt, updatedAt, syncState) " +
                                "VALUES ('vehicle-1', 'user-A', 'Vehicle', 10, 10, 'SYNCED')"
                        )
                    }
                })
                .build()
        )
        openHelper.writableDatabase
        openHelper.close()
    }

    private fun createVersion8Database() {
        createVersion7Database()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        check(oldVersion == 7 && newVersion == 8)
                        AppDatabase.migration7To8("user-A").migrate(db)
                    }
                })
                .build()
        )
        openHelper.writableDatabase
        openHelper.close()
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

    private fun vehicle() = DriveVehicleEntity(
        id = "vehicle-1",
        userId = "user-A",
        displayName = "Vehicle",
        createdAt = 10,
        updatedAt = 10,
        syncState = "PENDING_CREATE"
    )

    private fun trip() = DriveTripEntity(
        id = "drive-trip-1",
        userId = "user-A",
        vehicleId = "vehicle-1",
        startedAt = 20,
        distanceKm = 12.5,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = 20,
        updatedAt = 20,
        syncState = "PENDING_CREATE"
    )

    private fun operation() = DriveSyncOperationEntity(
        operationId = "operation-1",
        userId = "user-A",
        entityType = "VEHICLE",
        recordId = "vehicle-1",
        operationType = "CREATE_VEHICLE",
        createdAt = 10,
        updatedAt = 10
    )

    private companion object {
        const val DATABASE_NAME = "migration-7-8-test.db"
    }
}
