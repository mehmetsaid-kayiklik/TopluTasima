package com.example.toplutasima.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class TripDaoFlowRobolectricTest {
    private val userId = "flow-user"
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `flow emits empty insert update and delete states`() = runBlocking {
        val emissions = Channel<List<TripEntity>>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            database.tripDao().observeAllTrips(userId).collect(emissions::send)
        }

        assertTrue(emissions.next().isEmpty())
        database.tripDao().upsertAll(listOf(trip(id = "trip-1", line = "U1")))
        assertEquals(listOf("U1"), emissions.next().map { it.hat })
        database.tripDao().upsertAll(listOf(trip(id = "trip-1", line = "U2")))
        assertEquals(listOf("U2"), emissions.next().map { it.hat })
        database.tripDao().deleteTrip(userId, "trip-1")
        assertTrue(emissions.next().isEmpty())

        collection.cancelAndJoin()
    }

    @Test
    fun `flow is UID and month scoped and equals snapshot`() = runBlocking {
        database.tripDao().upsertAll(
            listOf(
                trip(id = "requested"),
                trip(id = "other-month", yearMonth = "2026-06"),
                trip(id = "other-user", owner = "other-user")
            )
        )

        val liveMonth = database.tripDao().observeTripsForMonth(userId, "2026-07").first()
        assertEquals(listOf("requested"), liveMonth.map { it.id })
        assertEquals(database.tripDao().getAllTrips(userId), database.tripDao().observeAllTrips(userId).first())
        assertEquals(
            database.tripDao().getTripsForMonth(userId, "2026-07"),
            liveMonth
        )
    }

    @Test
    fun `flow handles large fixture and stops after cancellation`() = runBlocking {
        val fixtureSize = 1_000
        database.tripDao().upsertAll(
            (0 until fixtureSize).map { index -> trip(id = "trip-$index") }
        )
        assertEquals(fixtureSize, database.tripDao().observeAllTrips(userId).first().size)

        val emissions = Channel<List<TripEntity>>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            database.tripDao().observeAllTrips(userId).collect(emissions::send)
        }
        emissions.next()
        collection.cancelAndJoin()
        database.tripDao().upsertAll(listOf(trip(id = "after-cancel")))
        assertNull(withTimeoutOrNull(300) { emissions.receive() })
    }

    @Test
    fun `month range flow is bounded and emits insert update and delete`() = runBlocking {
        database.tripDao().upsertAll(
            listOf(
                trip(id = "before-range", yearMonth = "2026-01"),
                trip(id = "in-range", line = "S1", yearMonth = "2026-05"),
                trip(id = "after-range", yearMonth = "2026-08")
            )
        )
        val emissions = Channel<List<TripEntity>>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            database.tripDao()
                .observeTripsForMonthRange(userId, "2026-02", "2026-07")
                .collect(emissions::send)
        }

        assertEquals(listOf("in-range"), emissions.next().map { it.id })

        database.tripDao().upsertAll(
            listOf(trip(id = "inserted", line = "U4", yearMonth = "2026-07"))
        )
        assertEquals(
            setOf("in-range", "inserted"),
            emissions.next().map { it.id }.toSet()
        )

        database.tripDao().upsertAll(
            listOf(trip(id = "in-range", line = "S8", yearMonth = "2026-05"))
        )
        assertEquals(
            "S8",
            emissions.next().single { it.id == "in-range" }.hat
        )

        database.tripDao().deleteTrip(userId, "inserted")
        assertEquals(listOf("in-range"), emissions.next().map { it.id })

        collection.cancelAndJoin()
    }

    @Test
    fun `month summary flow emits aggregate changes without loading trip rows`() = runBlocking {
        val emissions = Channel<List<com.example.toplutasima.data.local.dao.MonthSummaryTuple>>(
            Channel.UNLIMITED
        )
        val collection = launch(Dispatchers.Default) {
            database.tripDao().observeMonthSummaries(userId).collect(emissions::send)
        }

        assertTrue(emissions.nextMonthSummaries().isEmpty())
        database.tripDao().upsertAll(
            listOf(
                trip(id = "may-1", yearMonth = "2026-05"),
                trip(id = "may-2", yearMonth = "2026-05"),
                trip(id = "june-1", yearMonth = "2026-06"),
                trip(id = "other-user", yearMonth = "2026-05", owner = "other-user")
            )
        )
        assertEquals(
            listOf("2026-05" to 2, "2026-06" to 1),
            emissions.nextMonthSummaries().map { it.yearMonth to it.count }
        )

        database.tripDao().deleteTrip(userId, "may-1")
        assertEquals(
            listOf("2026-05" to 1, "2026-06" to 1),
            emissions.nextMonthSummaries().map { it.yearMonth to it.count }
        )

        collection.cancelAndJoin()
    }

    private suspend fun Channel<List<TripEntity>>.next(): List<TripEntity> =
        withTimeout(5_000) { receive() }

    private suspend fun Channel<List<com.example.toplutasima.data.local.dao.MonthSummaryTuple>>
        .nextMonthSummaries(): List<com.example.toplutasima.data.local.dao.MonthSummaryTuple> =
        withTimeout(5_000) { receive() }

    private fun trip(
        id: String,
        line: String = "S1",
        yearMonth: String = "2026-07",
        owner: String = userId
    ) = TripEntity(
        id = id,
        hat = line,
        yearMonth = yearMonth,
        sortDate = "2026-07-18",
        userId = owner
    )
}
