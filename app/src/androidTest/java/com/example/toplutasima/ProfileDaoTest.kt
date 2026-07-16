package com.example.toplutasima

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {

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
    fun testProfileDao_CRUD_Operations() = runBlocking {
        val profileDao = db.profileDao()
        val profile = TestProfileFixtures.profile(
            memoryNote = "Yol arkadasi"
        )

        profileDao.upsertAll(listOf(profile))

        val allProfiles = profileDao.getAllProfiles(TestProfileFixtures.USER_ID)
        assertEquals(1, allProfiles.size)
        assertEquals("Mehmet", allProfiles[0].displayName)

        val updatedProfile = profile.copy(displayName = "Mehmet Said", updatedAt = 2000L)
        profileDao.upsertAll(listOf(updatedProfile))

        val readUpdated = profileDao.getProfileById(TestProfileFixtures.USER_ID, "profile-1")
        assertNotNull(readUpdated)
        assertEquals("Mehmet Said", readUpdated?.displayName)
        assertEquals(2000L, readUpdated?.updatedAt)

        profileDao.deleteProfile(TestProfileFixtures.USER_ID, profile.id)
        assertNull(profileDao.getProfileById(TestProfileFixtures.USER_ID, "profile-1"))
    }

    @Test
    fun testProfileDao_UpsertPreservesTripProfileLinks() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()
        val profile = TestProfileFixtures.profile()
        val link = TestProfileFixtures.tripProfileLink(
            profileId = profile.id,
            seatmateNote = "Pencere kenari"
        )

        profileDao.upsert(profile)
        linkDao.upsert(link)

        profileDao.upsert(
            profile.copy(
                displayName = "Mehmet Said",
                updatedAt = 2000L
            )
        )

        val updatedProfile = profileDao.getProfileById(TestProfileFixtures.USER_ID, profile.id)
        val preservedLinks = linkDao.getLinksForTrip(TestProfileFixtures.USER_ID, link.tripStableKey)

        assertEquals("Mehmet Said", updatedProfile?.displayName)
        assertEquals(1, preservedLinks.size)
        assertEquals(link.id, preservedLinks.single().id)
        assertEquals("Pencere kenari", preservedLinks.single().seatmateNote)
    }
}
