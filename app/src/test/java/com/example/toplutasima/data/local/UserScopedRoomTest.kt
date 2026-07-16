package com.example.toplutasima.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.TripEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class UserScopedRoomTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `user B cannot see user A local trips after account switch`() = runBlocking {
        val dao = database.tripDao()
        dao.upsertAll(
            listOf(
                TripEntity(
                    id = "same-local-id",
                    tarih = "01.07.2026",
                    hat = "A record",
                    userId = "user-A"
                )
            )
        )

        assertEquals(listOf("A record"), dao.getAllTrips("user-A").map { it.hat })
        assertEquals(emptyList<TripEntity>(), dao.getAllTrips("user-B"))
        assertNull(dao.getTripById("user-B", "same-local-id"))

        dao.upsertAll(
            listOf(
                TripEntity(
                    id = "same-local-id",
                    tarih = "02.07.2026",
                    hat = "B record",
                    userId = "user-B"
                )
            )
        )

        assertEquals("A record", dao.getTripById("user-A", "same-local-id")?.hat)
        assertEquals("B record", dao.getTripById("user-B", "same-local-id")?.hat)
    }
}
