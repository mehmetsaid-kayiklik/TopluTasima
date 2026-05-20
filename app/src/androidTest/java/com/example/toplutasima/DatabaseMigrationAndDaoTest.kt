package com.example.toplutasima

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationAndDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testProfileDao_CRUD_Operations() = runBlocking {
        val profileDao = db.profileDao()

        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Mehmet",
            nameKind = "FIRST_NAME",
            memoryNote = "Yol arkadaşı",
            birthHint = "1990",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L,
            archived = false
        )

        // Insert
        profileDao.upsertAll(listOf(profile))

        // Read
        val allProfiles = profileDao.getAllProfiles()
        assertEquals(1, allProfiles.size)
        assertEquals("Mehmet", allProfiles[0].displayName)

        // Update
        val updatedProfile = profile.copy(displayName = "Mehmet Said", updatedAt = 2000L)
        profileDao.upsertAll(listOf(updatedProfile))

        val readUpdated = profileDao.getProfileById("profile-1")
        assertNotNull(readUpdated)
        assertEquals("Mehmet Said", readUpdated?.displayName)
        assertEquals(2000L, readUpdated?.updatedAt)

        // Delete
        profileDao.deleteProfile(profile.id)
        assertNull(profileDao.getProfileById("profile-1"))
    }

    @Test
    fun testTripProfileLinkDao_CascadeDelete() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()

        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Mehmet",
            nameKind = "FIRST_NAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        profileDao.upsertAll(listOf(profile))

        val link = TripProfileLinkEntity(
            id = "link-1",
            tripStableKey = "trip-key-123",
            profileId = "profile-1",
            seatmateNote = "Same row",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        linkDao.upsertAll(listOf(link))

        // Verify link is inserted
        assertEquals(1, linkDao.getAllLinks().size)

        // Delete profile and check if link is cascade deleted
        profileDao.deleteProfile("profile-1")
        assertEquals(0, linkDao.getAllLinks().size)
    }

    @Test
    fun testTripProfileLinkDao_UpdateStableKey() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()

        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Mehmet",
            nameKind = "FIRST_NAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        profileDao.upsertAll(listOf(profile))

        val link1 = TripProfileLinkEntity(
            id = "link-1",
            tripStableKey = "local-trip-uuid",
            profileId = "profile-1",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        linkDao.upsertAll(listOf(link1))

        // Update stable key from local-trip-uuid to firestore-doc-id
        linkDao.updateStableKey(
            oldStableKey = "local-trip-uuid",
            newStableKey = "firestore-doc-id",
            updatedAt = 2000L
        )

        val updatedLink = linkDao.getLinksForTrip("firestore-doc-id")
        assertEquals(1, updatedLink.size)
        assertEquals("link-1", updatedLink[0].id)
        assertEquals(2000L, updatedLink[0].updatedAt)
    }

    @Test
    fun testTripProfileLinkDao_DeleteLinksForTrip() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()

        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Mehmet",
            nameKind = "FIRST_NAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        profileDao.upsertAll(listOf(profile))

        val link1 = TripProfileLinkEntity(
            id = "link-1",
            tripStableKey = "trip-local-id",
            profileId = "profile-1",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val link2 = TripProfileLinkEntity(
            id = "link-2",
            tripStableKey = "trip-firestore-id",
            profileId = "profile-1",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        linkDao.upsertAll(listOf(link1, link2))

        assertEquals(2, linkDao.getAllLinks().size)

        // Delete links matching either trip-local-id or trip-firestore-id
        linkDao.deleteLinksForTrip("trip-local-id", "trip-firestore-id")

        assertEquals(0, linkDao.getAllLinks().size)
    }

    @Test
    fun testMigration4to5_PreservesTripsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbFile = context.getDatabasePath("test_migration_db")
        dbFile.delete()

        // Create db v4 schema manually
        val sqliteDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.version = 4
        sqliteDb.execSQL("""
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
        """)

        // Insert dummy trip to v4 DB
        val values = ContentValues().apply {
            put("id", "trip-1")
            put("firestoreDocId", "doc-1")
            put("hat", "S8")
            put("seatmateUuid", "sm-uuid-999")
        }
        sqliteDb.insert("trips", null, values)
        sqliteDb.close()

        // Open Room DB helper using standard SupportSQLiteOpenHelper to run migration
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration_db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Not called in migration test
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        if (oldVersion == 4 && newVersion == 5) {
                            AppDatabase.MIGRATION_4_5.migrate(db)
                        }
                    }
                })
                .build()
        )

        val writableSupportDb = openHelper.writableDatabase

        // Check if v5 tables were successfully created
        val cursorProfiles = writableSupportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='profiles'")
        assertTrue(cursorProfiles.moveToFirst())
        cursorProfiles.close()

        val cursorLinks = writableSupportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='trip_profile_links'")
        assertTrue(cursorLinks.moveToFirst())
        cursorLinks.close()

        // Check if old data in trips table is preserved
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

    @Test
    fun testProfileBackupManager_ExportImport_Success() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backupManager = com.example.toplutasima.data.backup.ProfileBackupManager(context, db)

        // 1. Setup initial profiles and links
        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Zeynep",
            nameKind = "FIRST_NAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val link = TripProfileLinkEntity(
            id = "link-1",
            tripStableKey = "trip-1",
            profileId = "profile-1",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        db.profileDao().upsertAll(listOf(profile))
        db.tripProfileLinkDao().upsertAll(listOf(link))

        val password = "StrongPassword999".toCharArray()

        // 2. Export
        val backupBytes = backupManager.exportBackup(password)
        assertNotNull(backupBytes)
        assertTrue(backupBytes.size > 50)

        // 3. Clear DB
        backupManager.clearBackupData()
        assertEquals(0, db.profileDao().getAllProfiles().size)
        assertEquals(0, db.tripProfileLinkDao().getAllLinks().size)

        // 4. Import
        val result = backupManager.importBackup(backupBytes, password)
        assertNull(result.error)
        assertEquals(1, result.addedProfiles)
        assertEquals(0, result.updatedProfiles)
        assertEquals(1, result.addedLinks)
        assertEquals(0, result.updatedLinks)
        assertEquals(0, result.skippedLinks)

        // 5. Verify restored
        val profiles = db.profileDao().getAllProfiles()
        assertEquals(1, profiles.size)
        assertEquals("Zeynep", profiles[0].displayName)

        val links = db.tripProfileLinkDao().getAllLinks()
        assertEquals(1, links.size)
        assertEquals("trip-1", links[0].tripStableKey)
    }

    @Test
    fun testProfileBackupManager_ConflictResolution() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backupManager = com.example.toplutasima.data.backup.ProfileBackupManager(context, db)
        val password = "StrongPassword999".toCharArray()

        // Create export with profile-1 (updatedAt = 1000L)
        val profileV1 = ProfileEntity(
            id = "profile-1",
            displayName = "Original Name",
            nameKind = "NICKNAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        db.profileDao().upsertAll(listOf(profileV1))
        val backupBytes = backupManager.exportBackup(password)

        // Case A: Database has a NEWER profile-1 (updatedAt = 2000L).
        // Importing the older backup (updatedAt = 1000L) should NOT overwrite it.
        val profileV2New = profileV1.copy(displayName = "Newer Name", updatedAt = 2000L)
        db.profileDao().upsertAll(listOf(profileV2New))

        val resultA = backupManager.importBackup(backupBytes, password)
        assertNull(resultA.error)
        assertEquals(0, resultA.addedProfiles)
        assertEquals(0, resultA.updatedProfiles) // Should be skipped since db has newer version

        val currentProfileA = db.profileDao().getProfileById("profile-1")
        assertEquals("Newer Name", currentProfileA?.displayName)

        // Case B: Database has an OLDER profile-1 (updatedAt = 500L).
        // Importing the backup (updatedAt = 1000L) SHOULD overwrite it.
        val profileV0Old = profileV1.copy(displayName = "Older Name", updatedAt = 500L)
        db.profileDao().upsertAll(listOf(profileV0Old))

        val resultB = backupManager.importBackup(backupBytes, password)
        assertNull(resultB.error)
        assertEquals(0, resultB.addedProfiles)
        assertEquals(1, resultB.updatedProfiles) // Overwritten

        val currentProfileB = db.profileDao().getProfileById("profile-1")
        assertEquals("Original Name", currentProfileB?.displayName)
    }

    @Test
    fun testProfileBackupManager_OrphanSkip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backupManager = com.example.toplutasima.data.backup.ProfileBackupManager(context, db)
        val password = "StrongPassword999".toCharArray()

        // Create a backup containing a link but NO profile in either backup or DB.
        // (For testing purposes, we construct the backup envelope using the manager by inserting then deleting,
        // but Room foreign key constraints prevent inserting a link without a profile when SQLite constraints are active.
        // SQLite foreign keys are active by default in Room. So let's bypass Room to craft an envelope or disable FKs temporarily.
        // Alternatively, we can verify that links pointing to missing profiles are skipped during import.)

        val profile = ProfileEntity(
            id = "profile-1",
            displayName = "Hasan",
            nameKind = "FIRST_NAME",
            infoSource = "ASKED",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val linkOrphan = TripProfileLinkEntity(
            id = "link-orphan",
            tripStableKey = "trip-2",
            profileId = "profile-nonexistent",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        
        db.profileDao().upsertAll(listOf(profile))
        // We can temporarily disable foreign key checking or insert linkOrphan directly using SupportSQLiteDatabase.
        // Let's do it using raw SQL since Room will enforce the FK on normal operations.
        db.openHelper.writableDatabase.execSQL(
            "PRAGMA foreign_keys = OFF"
        )
        db.tripProfileLinkDao().upsertAll(listOf(linkOrphan))

        val backupBytes = backupManager.exportBackup(password)

        // Clear everything
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        backupManager.clearBackupData()
        
        // Re-enable FKs to restore normal behavior
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")

        // Import: Hasan profile should be imported, linkOrphan should be skipped because profile-nonexistent doesn't exist.
        val result = backupManager.importBackup(backupBytes, password)
        assertNull(result.error)
        assertEquals(1, result.addedProfiles) // profile-1 Hasan
        assertEquals(0, result.addedLinks) // link-orphan is skipped
        assertEquals(1, result.skippedLinks) // link-orphan recorded as skipped
    }
}
