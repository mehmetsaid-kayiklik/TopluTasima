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
            memoryNote = "Yol arkadasi",
            birthHint = "1990"
        )

        profileDao.upsertAll(listOf(profile))

        val allProfiles = profileDao.getAllProfiles()
        assertEquals(1, allProfiles.size)
        assertEquals("Mehmet", allProfiles[0].displayName)

        val updatedProfile = profile.copy(displayName = "Mehmet Said", updatedAt = 2000L)
        profileDao.upsertAll(listOf(updatedProfile))

        val readUpdated = profileDao.getProfileById("profile-1")
        assertNotNull(readUpdated)
        assertEquals("Mehmet Said", readUpdated?.displayName)
        assertEquals(2000L, readUpdated?.updatedAt)

        profileDao.deleteProfile(profile.id)
        assertNull(profileDao.getProfileById("profile-1"))
    }
}
