package com.example.toplutasima.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.DriveExpenseEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveOdometerEntryEntity
import com.example.toplutasima.data.local.entity.DriveReminderEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class VehicleLedgerDaoTest {
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
    fun `odometer current query uses newest date in active series and isolates uid`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle("owner-a"))
        database.driveVehicleDao().upsert(vehicle("owner-b"))
        database.driveOdometerEntryDao().upsert(odometer("owner-a", "old-high", 10, 900_000))
        database.driveOdometerEntryDao().upsert(odometer("owner-a", "new", 20, 100_000))
        database.driveOdometerEntryDao().upsert(odometer("owner-b", "other", 30, 500_000))

        assertEquals("new", database.driveOdometerEntryDao()
            .observeCurrent("owner-a", VEHICLE_ID).first()?.odometerEntryId)
        assertEquals(listOf("new", "old-high"), database.driveOdometerEntryDao()
            .getAll("owner-a").map { it.odometerEntryId }.sorted())
        assertEquals("other", database.driveOdometerEntryDao()
            .observeCurrent("owner-b", VEHICLE_ID).first()?.odometerEntryId)
    }

    @Test
    fun `expense summary keeps currency tuples separate and signs refunds`() = runBlocking {
        database.driveExpenseDao().upsert(expense("expense-eur", "EUR", 2, 1_000, "EXPENSE"))
        database.driveExpenseDao().upsert(expense("refund-eur", "EUR", 2, 200, "REFUND"))
        database.driveExpenseDao().upsert(expense("expense-usd", "USD", 2, 500, "EXPENSE"))
        val rows = database.driveExpenseDao().observeSummary("owner-a", VEHICLE_ID, 0, 100).first()

        assertEquals(2, rows.size)
        assertEquals(800L, rows.single { it.currencyCode == "EUR" }.signedAmountMinor)
        assertEquals(500L, rows.single { it.currencyCode == "USD" }.signedAmountMinor)
    }

    @Test
    fun `reminder due query uses date or odometer and ignores disabled`() = runBlocking {
        database.driveReminderDao().upsert(reminder("date", dueDay = 5, dueMeters = null))
        database.driveReminderDao().upsert(reminder("distance", dueDay = null, dueMeters = 10_000))
        database.driveReminderDao().upsert(reminder("later", dueDay = 50, dueMeters = 50_000))
        database.driveReminderDao().upsert(reminder("disabled", dueDay = 1, dueMeters = null, status = "DISABLED"))

        assertEquals(
            listOf("date", "distance"),
            database.driveReminderDao().observeDue("owner-a", VEHICLE_ID, 10, 10_000)
                .first().map { it.reminderId }.sorted()
        )
    }

    @Test
    fun `operation claim is atomic and retry survives repository recreation`() = runBlocking {
        val operation = operation()
        database.driveLedgerOperationDao().upsert(operation)
        assertEquals(1, database.driveLedgerOperationDao().claim(
            "owner-a", operation.operationId, 10, "worker-a", 0
        ))
        assertEquals(0, database.driveLedgerOperationDao().claim(
            "owner-a", operation.operationId, 11, "worker-b", 0
        ))
        assertEquals(operation.operationId,
            database.driveLedgerOperationDao().pending("owner-a", 900_010, 10).single().operationId)
        assertEquals(1, database.driveLedgerOperationDao().claim(
            "owner-a", operation.operationId, 900_010, "worker-b", 11
        ))
        database.driveLedgerOperationDao().finishClaim(
            "owner-a", operation.operationId, "worker-b", "RETRY", 1, 900_020, "NETWORK", 900_012
        )
        assertTrue(database.driveLedgerOperationDao().pending("owner-a", 900_019, 10).isEmpty())
        assertEquals(operation.operationId,
            database.driveLedgerOperationDao().pending("owner-a", 900_020, 10).single().operationId)
    }

    @Test
    fun `upsert updates same primary key without creating duplicate`() = runBlocking {
        database.driveOdometerEntryDao().upsert(odometer("owner-a", "entry", 10, 100))
        database.driveOdometerEntryDao().upsert(
            odometer("owner-a", "entry", 11, 200).copy(revision = 2)
        )
        val rows = database.driveOdometerEntryDao().getAll("owner-a")
        assertEquals(1, rows.size)
        assertEquals(200L, rows.single().odometerMeters)
        assertNull(database.driveOdometerEntryDao().get("owner-b", "entry"))
    }

    private fun vehicle(owner: String) = DriveVehicleEntity(
        id = VEHICLE_ID, userId = owner, displayName = "Vehicle", createdAt = 1,
        updatedAt = 1, syncState = "SYNCED"
    )

    private fun odometer(owner: String, id: String, observedAt: Long, meters: Long) =
        DriveOdometerEntryEntity(
            owner, id, VEHICLE_ID, observedAt, meters, "CONFIRMED", "MANUAL", "series-1",
            null, null, null, null, null, 1, 1, operationId(id), "MANUAL", 1, 1,
            null, null, null, "SYNCED", null
        )

    private fun expense(id: String, currency: String, exponent: Int, amount: Long, kind: String) =
        DriveExpenseEntity(
            "owner-a", id, VEHICLE_ID, 10, "PARKING", kind, amount, currency, exponent,
            null, null, null, null, null, null, null, null, null, id, null,
            1, 1, operationId(id), "MANUAL", 1, 1, null, null, null, "SYNCED", null
        )

    private fun reminder(
        id: String,
        dueDay: Long?,
        dueMeters: Long?,
        status: String = "ACTIVE"
    ) = DriveReminderEntity(
        "owner-a", id, VEHICLE_ID, id, "OTHER", status, dueDay, dueMeters,
        null, null, "LAST_COMPLETION", null, null, null, null, null, null, null,
        null, 1, 1, operationId(id), "MANUAL", 1, 1, null, null, null, "SYNCED", null
    )

    private fun operation() = DriveLedgerOperationEntity(
        "owner-a", operationId("operation"), "batch", "EXPENSE", "expense", VEHICLE_ID,
        "CREATE", 1, "PENDING", 0, 0, null, null, null, 1, 1
    )

    private fun operationId(seed: String): String = java.util.UUID.nameUUIDFromBytes(seed.toByteArray()).toString()

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
    }
}
