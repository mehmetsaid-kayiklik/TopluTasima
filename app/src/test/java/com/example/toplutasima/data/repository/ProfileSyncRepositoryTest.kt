package com.example.toplutasima.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.network.firestore.FirestorePersonService.PersonShareState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class ProfileSyncRepositoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `refresh stores remote profiles for the current user and unshares stale local profiles`() =
        runBlocking {
            val dao = database.profileDao()
            dao.upsert(profile(id = "stale", userId = USER_A))
            val remoteProfile = profile(id = "remote", userId = "")
            val repository = ProfileSyncRepository(
                profileDao = dao,
                requireUserId = { USER_A },
                currentUserId = { USER_A },
                fetchSharedProfiles = { listOf(remoteProfile) },
                fetchShareStates = {
                    listOf(
                        PersonShareState(
                            id = "stale",
                            sharedWithTransit = false,
                            archived = false
                        ),
                        PersonShareState(
                            id = "remote",
                            sharedWithTransit = true,
                            archived = false
                        )
                    )
                }
            )

            val refreshed = repository.refreshSharedProfiles()

            assertEquals(listOf("remote"), refreshed.map { it.id })
            assertTrue(refreshed.single().sharedWithTransit)
            assertEquals(USER_A, refreshed.single().userId)
            val stale = dao.getProfileById(USER_A, "stale")
            assertNotNull(stale)
            assertFalse(stale!!.sharedWithTransit)
        }

    @Test
    fun `remote failure returns the current users cached shared profiles`() = runBlocking {
        val dao = database.profileDao()
        val cached = profile(id = "cached", userId = USER_A)
        dao.upsert(cached)
        val repository = ProfileSyncRepository(
            profileDao = dao,
            requireUserId = { USER_A },
            currentUserId = { USER_A },
            fetchSharedProfiles = { throw IllegalStateException("Firestore unavailable") },
            fetchShareStates = { error("share states must not be fetched after remote failure") }
        )

        val refreshed = repository.refreshSharedProfiles()

        assertEquals(listOf(cached), refreshed)
    }

    @Test
    fun `user change during refresh cancels without writing the previous users remote data`() =
        runBlocking {
            val dao = database.profileDao()
            var currentUser = USER_A
            val repository = ProfileSyncRepository(
                profileDao = dao,
                requireUserId = { USER_A },
                currentUserId = { currentUser },
                fetchSharedProfiles = {
                    currentUser = USER_B
                    listOf(profile(id = "remote-A", userId = ""))
                },
                fetchShareStates = { emptyList() }
            )

            try {
                repository.refreshSharedProfiles()
                fail("Expected the refresh to be cancelled after the authenticated user changed")
            } catch (_: CancellationException) {
                // Expected: no writes may cross the authenticated-user boundary.
            }

            assertTrue(dao.getAllProfiles(USER_A).isEmpty())
            assertTrue(dao.getAllProfiles(USER_B).isEmpty())
        }

    private fun profile(id: String, userId: String) = ProfileEntity(
        id = id,
        displayName = "Profile $id",
        createdAt = 100L,
        updatedAt = 200L,
        sharedWithTransit = true,
        userId = userId
    )

    private companion object {
        const val USER_A = "user-A"
        const val USER_B = "user-B"
    }
}
