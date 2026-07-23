package com.example.toplutasima.drive.ledger

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseTransactionKind

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class OfflineFirstVehicleLedgerRepositoryTest {
    private lateinit var database: AppDatabase
    private val auth = MutableStateFlow<String?>("owner-a")
    private var currentOwner: String? = "owner-a"
    private val scheduled = mutableListOf<String>()
    private lateinit var repository: OfflineFirstVehicleLedgerRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        database.driveVehicleDao().upsert(vehicle())
        repository = OfflineFirstVehicleLedgerRepository(
            database, { currentOwner }, auth, DriveSyncWorkScheduler { scheduled += it },
            now = { 100L }, enabled = true
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `expense create is local first with persistent operation`() = runBlocking {
        val result = repository.createExpense(expenseDraft())
        assertTrue(result is VehicleLedgerMutationResult.Success)
        val expense = database.driveExpenseDao().getAll("owner-a").single()
        assertEquals("LOCAL_PENDING", expense.syncState)
        assertEquals(1L, expense.revision)
        assertEquals(expense.operationId,
            database.driveLedgerOperationDao().pending("owner-a", 100, 10).single().operationId)
        assertEquals(listOf("owner-a"), scheduled)
    }

    @Test
    fun `legacy current odometer backfill is idempotent and has no fake observed date`() = runBlocking {
        val first = repository.runLegacyOdometerBackfill()
        val second = repository.runLegacyOdometerBackfill()
        assertEquals(1, (first as VehicleLedgerMutationResult.Success).value)
        assertEquals(0, (second as VehicleLedgerMutationResult.Success).value)
        val entry = database.driveOdometerEntryDao().getAll("owner-a").single()
        assertEquals(null, entry.observedAt)
        assertEquals("MIGRATED", entry.readingRole)
        assertEquals(12_345L, entry.odometerMeters)
    }

    @Test
    fun `duplicate expense remains separate and is only a candidate`() = runBlocking {
        val first = (repository.createExpense(expenseDraft()) as VehicleLedgerMutationResult.Success).value
        repository.createExpense(expenseDraft())
        val candidates = repository.duplicateExpenseCandidates(expenseDraft(), first.expenseId)
        assertEquals(1, candidates.size)
        assertEquals(2, database.driveExpenseDao().getAll("owner-a").size)
    }

    @Test
    fun `account flow never exposes previous owner records`() = runBlocking {
        repository.createExpense(expenseDraft())
        assertEquals(1, repository.observeExpenses(VEHICLE_ID, 0, Long.MAX_VALUE)
            .first { it.isNotEmpty() }.size)
        currentOwner = "owner-b"
        auth.value = "owner-b"
        assertTrue(repository.observeExpenses(VEHICLE_ID, 0, Long.MAX_VALUE).first().isEmpty())
    }

    @Test
    fun `vehicle tombstone wins child create`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle().copy(deletedAt = 99))
        val result = repository.createExpense(expenseDraft())
        assertEquals(
            VehicleLedgerFailure.VehicleDeleted,
            (result as VehicleLedgerMutationResult.Rejected).failure
        )
        assertTrue(database.driveExpenseDao().getAll("owner-a").isEmpty())
    }

    @Test
    fun `disabled gate performs no room write or scheduling`() = runBlocking {
        val disabled = OfflineFirstVehicleLedgerRepository(
            database, { currentOwner }, auth, DriveSyncWorkScheduler { scheduled += it },
            now = { 100L }, enabled = false
        )
        val result = disabled.createExpense(expenseDraft())
        assertEquals(VehicleLedgerFailure.FeatureDisabled,
            (result as VehicleLedgerMutationResult.Rejected).failure)
        assertTrue(database.driveExpenseDao().getAll("owner-a").isEmpty())
        assertTrue(scheduled.isEmpty())
    }

    private fun vehicle() = DriveVehicleEntity(
        VEHICLE_ID, "owner-a", "Vehicle", currentOdometerKm = 12.345,
        createdAt = 1, updatedAt = 1, syncState = "SYNCED"
    )

    private fun expenseDraft() = ExpenseDraft(
        VEHICLE_ID, 50, VehicleExpenseCategory.PARKING,
        VehicleExpenseTransactionKind.EXPENSE, 500, "EUR", 2,
        vendorName = "Garage"
    )

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
    }
}
