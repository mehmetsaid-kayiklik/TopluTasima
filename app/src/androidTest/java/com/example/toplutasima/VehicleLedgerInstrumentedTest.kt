package com.example.toplutasima

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveExpenseEntity
import com.example.toplutasima.data.local.entity.DriveOdometerEntryEntity
import com.example.toplutasima.data.local.entity.DriveReminderEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.ledger.VehicleLedgerRoute
import com.example.toplutasima.drive.sync.DriveAccountScopeManager
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleLedgerInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun currentOdometerFlow_isUidScopedAndDateOrdered() = runBlocking {
        database.driveOdometerEntryDao().upsert(odometer("uid-a", "old", 1, 500_000))
        database.driveOdometerEntryDao().upsert(odometer("uid-a", "new", 2, 100_000))
        database.driveOdometerEntryDao().upsert(odometer("uid-b", "other", 3, 900_000))
        assertEquals("new", database.driveOdometerEntryDao()
            .observeCurrent("uid-a", VEHICLE_ID).first()?.odometerEntryId)
        assertEquals("other", database.driveOdometerEntryDao()
            .observeCurrent("uid-b", VEHICLE_ID).first()?.odometerEntryId)
    }

    @Test
    fun expenseSummaryAndReminderDue_queriesRemainDeterministic() = runBlocking {
        database.driveExpenseDao().upsert(expense("cost", "EXPENSE", 1_000))
        database.driveExpenseDao().upsert(expense("refund", "REFUND", 250))
        assertEquals(750L, database.driveExpenseDao()
            .observeSummary("uid-a", VEHICLE_ID, 0, 20).first().single().signedAmountMinor)
        database.driveReminderDao().upsert(reminder("date", 5, null))
        database.driveReminderDao().upsert(reminder("distance", null, 10_000))
        assertEquals(2, database.driveReminderDao()
            .observeDue("uid-a", VEHICLE_ID, 5, 10_000).first().size)
    }

    @Test
    fun accountCleanup_removesOldUidBeforeNewScopeIsObserved() = runBlocking {
        database.driveVehicleDao().upsert(vehicle("uid-a"))
        database.driveVehicleDao().upsert(vehicle("uid-b"))
        database.driveOdometerEntryDao().upsert(odometer("uid-a", "a", 1, 1))
        database.driveOdometerEntryDao().upsert(odometer("uid-b", "b", 1, 1))
        val manager = DriveAccountScopeManager(
            databaseProvider = { database }, onAuthenticatedUserChanged = {},
            authChanges = emptyFlow(), currentUserId = { "uid-b" }, enabled = true
        )
        manager.purgeOutsideActiveAccount("uid-b")
        assertTrue(database.driveOdometerEntryDao().getAll("uid-a").isEmpty())
        assertEquals(1, database.driveOdometerEntryDao().getAll("uid-b").size)
        assertNull(database.driveVehicleDao().getVehicle("uid-a", VEHICLE_ID))
    }

    @Test
    fun ledgerNavigation_rejectsUidSnapshotAndMalformedVehicleRoutes() {
        assertEquals("vehicle-1", VehicleLedgerRoute.parse(
            "drive/vehicle/vehicle-1/reminders"
        )?.vehicleId)
        assertNull(VehicleLedgerRoute.parse("drive/vehicle/vehicle-1/reminders/uid-a"))
        assertNull(VehicleLedgerRoute.parse("drive/vehicle/bad/id/expenses"))
    }

    private fun vehicle(uid: String) = DriveVehicleEntity(
        VEHICLE_ID, uid, "Vehicle", createdAt = 1, updatedAt = 1, syncState = "SYNCED"
    )

    private fun odometer(uid: String, id: String, observedAt: Long, meters: Long) =
        DriveOdometerEntryEntity(
            uid, id, VEHICLE_ID, observedAt, meters, "CONFIRMED", "MANUAL", "series",
            null, null, null, null, null, 1, 1, operation(id), "MANUAL", 1, 1,
            null, null, null, "SYNCED", null
        )

    private fun expense(id: String, kind: String, amount: Long) = DriveExpenseEntity(
        "uid-a", id, VEHICLE_ID, 10, "PARKING", kind, amount, "EUR", 2,
        null, null, null, null, null, null, null, null, null, id, null,
        1, 1, operation(id), "MANUAL", 1, 1, null, null, null, "SYNCED", null
    )

    private fun reminder(id: String, dueDay: Long?, dueMeters: Long?) = DriveReminderEntity(
        "uid-a", id, VEHICLE_ID, id, "OTHER", "ACTIVE", dueDay, dueMeters,
        null, null, "LAST_COMPLETION", null, null, null, null, null, null, null, null,
        1, 1, operation(id), "MANUAL", 1, 1, null, null, null, "SYNCED", null
    )

    private fun operation(seed: String) = java.util.UUID.nameUUIDFromBytes(seed.toByteArray()).toString()

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
    }
}
