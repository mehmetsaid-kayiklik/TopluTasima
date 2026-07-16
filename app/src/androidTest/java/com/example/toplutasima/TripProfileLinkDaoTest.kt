package com.example.toplutasima

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripProfileLinkDaoTest {

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
    fun testTripProfileLinkDao_CascadeDelete() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()
        val profile = TestProfileFixtures.profile()
        val link = TestProfileFixtures.tripProfileLink(seatmateNote = "Same row")

        profileDao.upsertAll(listOf(profile))
        linkDao.upsertAll(listOf(link))

        assertEquals(1, linkDao.getAllLinks(TestProfileFixtures.USER_ID).size)

        profileDao.deleteProfile(TestProfileFixtures.USER_ID, "profile-1")
        assertEquals(0, linkDao.getAllLinks(TestProfileFixtures.USER_ID).size)
    }

    @Test
    fun testTripProfileLinkDao_UpdateStableKey() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()
        val profile = TestProfileFixtures.profile()
        val link = TestProfileFixtures.tripProfileLink(
            tripStableKey = "local-trip-uuid"
        )

        profileDao.upsertAll(listOf(profile))
        linkDao.upsertAll(listOf(link))

        linkDao.updateStableKey(
            userId = TestProfileFixtures.USER_ID,
            oldStableKey = "local-trip-uuid",
            newStableKey = "firestore-doc-id",
            updatedAt = 2000L
        )

        val updatedLink = linkDao.getLinksForTrip(TestProfileFixtures.USER_ID, "firestore-doc-id")
        assertEquals(1, updatedLink.size)
        assertEquals("link-1", updatedLink[0].id)
        assertEquals(2000L, updatedLink[0].updatedAt)
    }

    @Test
    fun testTripProfileLinkDao_DeleteLinksForTrip() = runBlocking {
        val profileDao = db.profileDao()
        val linkDao = db.tripProfileLinkDao()
        val profile = TestProfileFixtures.profile()
        val link1 = TestProfileFixtures.tripProfileLink(
            id = "link-1",
            tripStableKey = "trip-local-id"
        )
        val link2 = TestProfileFixtures.tripProfileLink(
            id = "link-2",
            tripStableKey = "trip-firestore-id"
        )

        profileDao.upsertAll(listOf(profile))
        linkDao.upsertAll(listOf(link1, link2))

        assertEquals(2, linkDao.getAllLinks(TestProfileFixtures.USER_ID).size)

        linkDao.deleteLinksForTrip(
            TestProfileFixtures.USER_ID,
            "trip-local-id",
            "trip-firestore-id"
        )

        assertEquals(0, linkDao.getAllLinks(TestProfileFixtures.USER_ID).size)
    }
}
