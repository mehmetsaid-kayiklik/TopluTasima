package com.example.toplutasima

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
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

@RunWith(AndroidJUnit4::class)
class TripDaoFlowTest {
    private val userId = "flow-user"
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabaseFactory.createInMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allTripsFlowEmitsForInsertUpdateAndDelete() = runBlocking {
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
    fun monthFlowEmitsOnlyRequestedUsersMonth() = runBlocking {
        database.tripDao().upsertAll(
            listOf(
                trip(id = "requested", yearMonth = "2026-07"),
                trip(id = "other-month", yearMonth = "2026-06"),
                trip(id = "other-user", yearMonth = "2026-07", owner = "other-user")
            )
        )

        val result = database.tripDao().observeTripsForMonth(userId, "2026-07").first()

        assertEquals(listOf("requested"), result.map { it.id })
    }

    @Test
    fun liveQueriesAreEquivalentToSnapshotQueries() = runBlocking {
        database.tripDao().upsertAll(
            listOf(
                trip(id = "july-new", yearMonth = "2026-07", sortDate = "2026-07-18"),
                trip(id = "july-old", yearMonth = "2026-07", sortDate = "2026-07-01"),
                trip(id = "june", yearMonth = "2026-06", sortDate = "2026-06-30")
            )
        )

        assertEquals(
            database.tripDao().getAllTrips(userId),
            database.tripDao().observeAllTrips(userId).first()
        )
        assertEquals(
            database.tripDao().getTripsForMonth(userId, "2026-07"),
            database.tripDao().observeTripsForMonth(userId, "2026-07").first()
        )
    }

    @Test
    fun liveQueryHandlesLargeFixture() = runBlocking {
        val fixtureSize = 1_000
        database.tripDao().upsertAll(
            (0 until fixtureSize).map { index ->
                trip(
                    id = "trip-$index",
                    sortDate = "2026-07-${(index % 28 + 1).toString().padStart(2, '0')}"
                )
            }
        )

        val result = database.tripDao().observeAllTrips(userId).first()

        assertEquals(fixtureSize, result.size)
        assertEquals(fixtureSize, result.map { it.id }.toSet().size)
    }

    @Test
    fun cancellingCollectionStopsFurtherEmissions() = runBlocking {
        val emissions = Channel<List<TripEntity>>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            database.tripDao().observeAllTrips(userId).collect(emissions::send)
        }
        emissions.next()

        collection.cancelAndJoin()
        database.tripDao().upsertAll(listOf(trip(id = "after-cancel")))

        assertNull(withTimeoutOrNull(300) { emissions.receive() })
    }

    private suspend fun Channel<List<TripEntity>>.next(): List<TripEntity> =
        withTimeout(5_000) { receive() }

    private fun trip(
        id: String,
        line: String = "S1",
        yearMonth: String = "2026-07",
        sortDate: String = "2026-07-18",
        owner: String = userId
    ) = TripEntity(
        id = id,
        hat = line,
        yearMonth = yearMonth,
        sortDate = sortDate,
        userId = owner
    )
}
