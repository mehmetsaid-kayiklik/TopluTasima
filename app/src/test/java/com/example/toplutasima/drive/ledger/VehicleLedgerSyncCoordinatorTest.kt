package com.example.toplutasima.drive.ledger

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import shared.vehicleledger.contract.LedgerServerTimestamp
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerEntityType

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class VehicleLedgerSyncCoordinatorTest {
    private lateinit var database: AppDatabase
    private var owner: String? = OWNER
    private lateinit var remote: FakeLedgerRemote
    private lateinit var repository: OfflineFirstVehicleLedgerRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        database.driveVehicleDao().upsert(vehicle())
        remote = FakeLedgerRemote()
        repository = OfflineFirstVehicleLedgerRepository(
            database, { owner }, MutableStateFlow(owner), DriveSyncWorkScheduler {},
            now = { 100 }, enabled = true
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `outbox apply writes synced projection receipt and keeps revision stable`() = runBlocking {
        repository.createExpense(draft())
        val local = database.driveExpenseDao().getAll(OWNER).single()
        val coordinator = coordinator()
        val first = coordinator.synchronize(OWNER)
        val second = coordinator.synchronize(OWNER)

        assertEquals(1, first.processedCount)
        assertEquals(1, remote.applyCount)
        assertEquals(1L, database.driveExpenseDao().get(OWNER, local.expenseId)?.revision)
        assertEquals("SYNCED", database.driveExpenseDao().get(OWNER, local.expenseId)?.syncState)
        assertEquals("SUCCEEDED", database.driveLedgerOperationDao().get(OWNER, local.operationId)?.state)
        assertNotNull(database.driveLedgerSyncReceiptDao().getByOperation(OWNER, local.operationId))
        assertEquals(0, second.processedCount)
        assertEquals(1, remote.applyCount)
    }

    @Test
    fun `vehicle deletion supersedes pending child without remote write or data loss`() = runBlocking {
        repository.createExpense(draft())
        val local = database.driveExpenseDao().getAll(OWNER).single()
        database.driveVehicleDao().upsert(vehicle().copy(deletedAt = 101))

        coordinator().synchronize(OWNER)

        assertEquals(0, remote.applyCount)
        assertNotNull(database.driveExpenseDao().get(OWNER, local.expenseId))
        assertEquals("SUPERSEDED", database.driveLedgerOperationDao().get(OWNER, local.operationId)?.state)
    }

    @Test
    fun `remote tombstone wins and local candidate is retained in conflict shadow`() = runBlocking {
        repository.createExpense(draft())
        val local = database.driveExpenseDao().getAll(OWNER).single().toContract()
        val remoteTombstone = local.copy(envelope = local.envelope.copy(
            revision = 2,
            operationId = "22222222-2222-4222-8222-222222222222",
            serverUpdatedAt = LedgerServerTimestamp(2, 0),
            deletedAt = 200
        ))
        remote.initial[VehicleLedgerEntityType.EXPENSE] = VehicleLedgerPullBatch(
            listOf(VehicleLedgerRecord.Expense(remoteTombstone)), emptyList(), emptyList(),
            VehicleLedgerRemoteCursor(2, 0, local.expenseId), 1
        )

        coordinator().synchronize(OWNER)

        val stored = database.driveExpenseDao().get(OWNER, local.expenseId)
        assertEquals(200L, stored?.deletedAt)
        assertEquals("CONFLICT", stored?.syncState)
        val conflict = database.driveLedgerConflictDao().observeUnresolved(OWNER).first().single()
        assertTrue(conflict.localSnapshotJson.contains(local.envelope.operationId))
        assertTrue(conflict.remoteSnapshotJson.contains(remoteTombstone.envelope.operationId))
    }

    private fun coordinator() = RoomVehicleLedgerSyncCoordinator(
        database, remote, { owner }, now = { 300 }
    )

    private fun vehicle() = DriveVehicleEntity(
        VEHICLE_ID, OWNER, "Vehicle", createdAt = 1, updatedAt = 1, syncState = "SYNCED"
    )

    private fun draft() = ExpenseDraft(
        VEHICLE_ID, 10, VehicleExpenseCategory.PARKING,
        VehicleExpenseTransactionKind.EXPENSE, 100, "EUR", 2
    )

    private class FakeLedgerRemote : VehicleLedgerRemoteDataSource {
        val initial = mutableMapOf<VehicleLedgerEntityType, VehicleLedgerPullBatch>()
        var applyCount = 0

        override suspend fun fetchInitial(ownerUid: String, entityType: VehicleLedgerEntityType) =
            initial[entityType] ?: VehicleLedgerPullBatch(emptyList(), emptyList(), emptyList(), null, 0)

        override suspend fun fetchIncremental(
            ownerUid: String,
            entityType: VehicleLedgerEntityType,
            cursor: VehicleLedgerRemoteCursor?
        ) = VehicleLedgerPullBatch(emptyList(), emptyList(), emptyList(), cursor, 0)

        override suspend fun apply(
            ownerUid: String,
            operation: DriveLedgerOperationEntity,
            local: VehicleLedgerRecord
        ): VehicleLedgerRemoteApplyResult {
            applyCount++
            val timestamp = LedgerServerTimestamp(3, 0)
            val remote = when (local) {
                is VehicleLedgerRecord.Odometer -> VehicleLedgerRecord.Odometer(
                    local.value.copy(envelope = local.envelope.copy(serverUpdatedAt = timestamp))
                )
                is VehicleLedgerRecord.Expense -> VehicleLedgerRecord.Expense(
                    local.value.copy(envelope = local.envelope.copy(serverUpdatedAt = timestamp))
                )
                is VehicleLedgerRecord.Reminder -> VehicleLedgerRecord.Reminder(
                    local.value.copy(envelope = local.envelope.copy(serverUpdatedAt = timestamp))
                )
            }
            return VehicleLedgerRemoteApplyResult.Applied(remote)
        }

        override suspend fun reconcileOdometerMirror(
            ownerUid: String,
            vehicleId: String,
            currentOdometerKm: Double?,
            targetRevision: Long,
            operationId: String
        ) = OdometerMirrorRemoteResult.Applied
    }

    private companion object {
        const val OWNER = "owner-a"
        const val VEHICLE_ID = "vehicle-1"
    }
}
