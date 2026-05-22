package com.example.toplutasima

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.backup.ProfileBackupManager
import com.example.toplutasima.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBackupManagerInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = TestDatabaseFactory.createInMemoryDatabase()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testProfileBackupManager_ExportImport_Success() = runBlocking {
        val backupManager = ProfileBackupManager(TestDatabaseFactory.targetContext(), db)
        val profile = TestProfileFixtures.profile(
            displayName = "Zeynep"
        )
        val link = TestProfileFixtures.tripProfileLink(
            tripStableKey = "trip-1"
        )

        db.profileDao().upsertAll(listOf(profile))
        db.tripProfileLinkDao().upsertAll(listOf(link))

        val password = "StrongPassword999".toCharArray()
        val backupBytes = backupManager.exportBackup(password)
        assertNotNull(backupBytes)
        assertTrue(backupBytes.size > 50)

        backupManager.clearBackupData()
        assertEquals(0, db.profileDao().getAllProfiles().size)
        assertEquals(0, db.tripProfileLinkDao().getAllLinks().size)

        val result = backupManager.importBackup(backupBytes, password)
        assertNull(result.error)
        assertEquals(1, result.addedProfiles)
        assertEquals(0, result.updatedProfiles)
        assertEquals(1, result.addedLinks)
        assertEquals(0, result.updatedLinks)
        assertEquals(0, result.skippedLinks)

        val profiles = db.profileDao().getAllProfiles()
        assertEquals(1, profiles.size)
        assertEquals("Zeynep", profiles[0].displayName)

        val links = db.tripProfileLinkDao().getAllLinks()
        assertEquals(1, links.size)
        assertEquals("trip-1", links[0].tripStableKey)
    }

    @Test
    fun testProfileBackupManager_ConflictResolution() = runBlocking {
        val backupManager = ProfileBackupManager(TestDatabaseFactory.targetContext(), db)
        val password = "StrongPassword999".toCharArray()
        val profileV1 = TestProfileFixtures.profile(
            displayName = "Original Name",
            nameKind = "NICKNAME",
            updatedAt = 1000L
        )

        db.profileDao().upsertAll(listOf(profileV1))
        val backupBytes = backupManager.exportBackup(password)

        val profileV2New = profileV1.copy(displayName = "Newer Name", updatedAt = 2000L)
        db.profileDao().upsertAll(listOf(profileV2New))

        val resultA = backupManager.importBackup(backupBytes, password)
        assertNull(resultA.error)
        assertEquals(0, resultA.addedProfiles)
        assertEquals(0, resultA.updatedProfiles)

        val currentProfileA = db.profileDao().getProfileById("profile-1")
        assertEquals("Newer Name", currentProfileA?.displayName)

        val profileV0Old = profileV1.copy(displayName = "Older Name", updatedAt = 500L)
        db.profileDao().upsertAll(listOf(profileV0Old))

        val resultB = backupManager.importBackup(backupBytes, password)
        assertNull(resultB.error)
        assertEquals(0, resultB.addedProfiles)
        assertEquals(1, resultB.updatedProfiles)

        val currentProfileB = db.profileDao().getProfileById("profile-1")
        assertEquals("Original Name", currentProfileB?.displayName)
    }

    @Test
    fun testProfileBackupManager_OrphanSkip() = runBlocking {
        val backupManager = ProfileBackupManager(TestDatabaseFactory.targetContext(), db)
        val password = "StrongPassword999".toCharArray()
        val profile = TestProfileFixtures.profile(
            displayName = "Hasan"
        )
        val linkOrphan = TestProfileFixtures.tripProfileLink(
            id = "link-orphan",
            tripStableKey = "trip-2",
            profileId = "profile-nonexistent"
        )

        db.profileDao().upsertAll(listOf(profile))
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        db.tripProfileLinkDao().upsertAll(listOf(linkOrphan))

        val backupBytes = backupManager.exportBackup(password)

        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        backupManager.clearBackupData()
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")

        val result = backupManager.importBackup(backupBytes, password)
        assertNull(result.error)
        assertEquals(1, result.addedProfiles)
        assertEquals(0, result.addedLinks)
        assertEquals(1, result.skippedLinks)
    }
}
