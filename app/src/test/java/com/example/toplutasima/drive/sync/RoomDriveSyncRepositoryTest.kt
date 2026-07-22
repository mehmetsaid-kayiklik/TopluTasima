package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.dao.DriveSyncOperationDao
import com.example.toplutasima.data.local.dao.DriveTripDao
import com.example.toplutasima.data.local.dao.DriveTripSummary
import com.example.toplutasima.data.local.dao.DriveVehicleDao
import com.example.toplutasima.data.local.dao.DriveVehicleWithSummary
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.data.remote.DriveRemoteDataSource
import com.example.toplutasima.drive.model.DriveSyncState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDriveSyncRepositoryTest {
    @Test
    fun `successful create is acknowledged once and is idempotent after restart`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.CREATE_VEHICLE))

        val first = fixture.repository.synchronize(USER_ID)
        val second = fixture.repository.synchronize(USER_ID)

        assertEquals(1, first.processedCount)
        assertEquals(0, second.processedCount)
        assertEquals(1, fixture.remote.vehicleUpsertCalls)
        assertNull(
            fixture.operations.getOperation(
                USER_ID,
                DriveSyncEntityType.VEHICLE.name,
                VEHICLE_ID
            )
        )
        assertEquals(
            DriveSyncState.SYNCED.name,
            fixture.vehicles.getVehicle(USER_ID, VEHICLE_ID)?.syncState
        )
    }

    @Test
    fun `retryable failure preserves operation and sanitized retry metadata`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.UPDATE_VEHICLE))
        fixture.remote.vehicleUpsert = { _, _, _ -> throw IOException("offline") }

        val result = fixture.repository.synchronize(USER_ID)
        val queued = fixture.operations.getOperation(
            USER_ID,
            DriveSyncEntityType.VEHICLE.name,
            VEHICLE_ID
        )

        assertTrue(result.retryRequired)
        assertNotNull(queued)
        assertEquals(1, queued?.attemptCount)
        assertEquals(DriveSyncFailureCode.NETWORK.name, queued?.lastErrorCode)
        assertEquals(true, queued?.retryEligible)
        assertEquals(
            DriveSyncState.RETRYABLE_ERROR.name,
            fixture.vehicles.getVehicle(USER_ID, VEHICLE_ID)?.syncState
        )
    }

    @Test
    fun `fatal validation failure is retained without retry eligibility`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.UPDATE_VEHICLE))
        fixture.remote.vehicleUpsert = { _, _, _ -> throw IllegalArgumentException("invalid") }

        val result = fixture.repository.synchronize(USER_ID)
        val queued = fixture.operations.getOperation(
            USER_ID,
            DriveSyncEntityType.VEHICLE.name,
            VEHICLE_ID
        )

        assertEquals(1, result.permanentFailureCount)
        assertEquals(false, queued?.retryEligible)
        assertEquals(DriveSyncFailureCode.INVALID_DATA.name, queued?.lastErrorCode)
        assertEquals(
            DriveSyncState.PERMANENT_ERROR.name,
            fixture.vehicles.getVehicle(USER_ID, VEHICLE_ID)?.syncState
        )
    }

    @Test
    fun `authentication change after remote write keeps the operation for original owner`() {
        val fixture = Fixture()
        runBlocking {
            fixture.vehicles.upsert(vehicle())
            fixture.operations.upsert(operation(DriveSyncOperationType.CREATE_VEHICLE))
        }
        fixture.remote.vehicleUpsert = { _, _, _ ->
            fixture.authenticatedUserId = "other-user"
            DriveRemoteWriteResult.Applied
        }

        var cancelled = false
        try {
            runBlocking { fixture.repository.synchronize(USER_ID) }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        runBlocking {
            assertNotNull(
                fixture.operations.getOperation(
                    USER_ID,
                    DriveSyncEntityType.VEHICLE.name,
                    VEHICLE_ID
                )
            )
        }
    }

    @Test
    fun `new delete token cannot be acknowledged by stale update completion`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.UPDATE_VEHICLE))
        fixture.remote.vehicleUpsert = { _, _, _ ->
            fixture.vehicles.upsert(
                vehicle().copy(
                    deletedAt = 200L,
                    updatedAt = 200L,
                    syncState = DriveSyncState.LOCAL_PENDING.name
                )
            )
            fixture.operations.upsert(
                operation(DriveSyncOperationType.DELETE_VEHICLE).copy(
                    operationId = "delete-token",
                    updatedAt = 200L,
                    nextAttemptAt = 10_000L
                )
            )
            DriveRemoteWriteResult.Applied
        }

        fixture.repository.synchronize(USER_ID)

        val queued = fixture.operations.getOperation(
            USER_ID,
            DriveSyncEntityType.VEHICLE.name,
            VEHICLE_ID
        )
        assertEquals("delete-token", queued?.operationId)
        assertEquals(DriveSyncOperationType.DELETE_VEHICLE.name, queued?.operationType)
    }

    @Test
    fun `stale update failure cannot mark a newer delete as failed`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.UPDATE_VEHICLE))
        fixture.remote.vehicleUpsert = { _, _, _ ->
            fixture.vehicles.upsert(
                vehicle().copy(
                    deletedAt = 200L,
                    updatedAt = 200L,
                    syncState = DriveSyncState.LOCAL_PENDING.name
                )
            )
            fixture.operations.upsert(
                operation(DriveSyncOperationType.DELETE_VEHICLE).copy(
                    operationId = "delete-token",
                    updatedAt = 200L,
                    nextAttemptAt = 10_000L
                )
            )
            throw IOException("stale update failed")
        }

        val result = fixture.repository.synchronize(USER_ID)

        val queued = fixture.operations.getOperation(
            USER_ID,
            DriveSyncEntityType.VEHICLE.name,
            VEHICLE_ID
        )
        assertEquals(0, result.permanentFailureCount)
        assertEquals(false, result.retryRequired)
        assertEquals("delete-token", queued?.operationId)
        assertEquals(0, queued?.attemptCount)
        assertEquals(null, queued?.lastErrorCode)
        assertEquals(
            DriveSyncState.LOCAL_PENDING.name,
            fixture.vehicles.getVehicle(USER_ID, VEHICLE_ID)?.syncState
        )
    }

    @Test
    fun `remote tombstone takes precedence over pending local update`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(vehicle())
        fixture.operations.upsert(operation(DriveSyncOperationType.UPDATE_VEHICLE))
        fixture.remote.vehicleUpsert = { _, _, _ ->
            DriveRemoteWriteResult.DeletePrecedence(deletedAtEpochMillis = 150L)
        }

        val result = fixture.repository.synchronize(USER_ID)

        assertEquals(1, result.processedCount)
        assertEquals(150L, fixture.vehicles.getVehicle(USER_ID, VEHICLE_ID)?.deletedAt)
        assertNull(
            fixture.operations.getOperation(
                USER_ID,
                DriveSyncEntityType.VEHICLE.name,
                VEHICLE_ID
            )
        )
    }

    @Test
    fun `trip upsert waits until its parent vehicle is synced even when trip sorts first`() =
        runBlocking {
            val fixture = Fixture()
            fixture.vehicles.upsert(vehicle())
            fixture.trips.upsert(trip())
            fixture.operations.upsert(tripOperation(DriveSyncOperationType.CREATE_DRIVE_TRIP))
            fixture.operations.upsert(operation(DriveSyncOperationType.CREATE_VEHICLE))

            val first = fixture.repository.synchronize(USER_ID)

            assertEquals(1, first.processedCount)
            assertTrue(first.retryRequired)
            assertEquals(1, fixture.remote.vehicleUpsertCalls)
            assertEquals(0, fixture.remote.tripUpsertCalls)

            fixture.clock = 40_000L
            val second = fixture.repository.synchronize(USER_ID)

            assertEquals(1, second.processedCount)
            assertEquals(1, fixture.remote.tripUpsertCalls)
        }

    @Test
    fun `trip tombstone remains processable after parent vehicle deletion`() = runBlocking {
        val fixture = Fixture()
        fixture.vehicles.upsert(
            vehicle().copy(
                deletedAt = 150L,
                updatedAt = 150L,
                syncState = DriveSyncState.SYNCED.name
            )
        )
        fixture.trips.upsert(
            trip().copy(
                deletedAt = 150L,
                updatedAt = 150L,
                syncState = DriveSyncState.LOCAL_PENDING.name
            )
        )
        fixture.operations.upsert(tripOperation(DriveSyncOperationType.DELETE_DRIVE_TRIP))

        val result = fixture.repository.synchronize(USER_ID)

        assertEquals(1, result.processedCount)
        assertEquals(1, fixture.remote.tripTombstoneCalls)
    }

    @Test
    fun `cancellation is rethrown and does not acknowledge operation`() {
        val fixture = Fixture()
        runBlocking {
            fixture.vehicles.upsert(vehicle())
            fixture.operations.upsert(operation(DriveSyncOperationType.CREATE_VEHICLE))
        }
        fixture.remote.vehicleUpsert = { _, _, _ -> throw CancellationException("cancel") }

        var cancelled = false
        try {
            runBlocking { fixture.repository.synchronize(USER_ID) }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        runBlocking {
            assertNotNull(
                fixture.operations.getOperation(
                    USER_ID,
                    DriveSyncEntityType.VEHICLE.name,
                    VEHICLE_ID
                )
            )
        }
    }

    private class Fixture {
        val vehicles = FakeVehicleDao()
        val trips = FakeTripDao()
        val operations = FakeOperationDao()
        val remote = FakeRemoteDataSource()
        var authenticatedUserId: String? = USER_ID
        var clock = 1_000L
        val repository = RoomDriveSyncRepository(
            vehicleDao = vehicles,
            tripDao = trips,
            operationDao = operations,
            remoteDataSource = remote,
            transactionRunner = DriveSyncTransactionRunner { block -> block() },
            currentUserId = { authenticatedUserId },
            now = { clock }
        )
    }

    private class FakeRemoteDataSource : DriveRemoteDataSource {
        var vehicleUpsertCalls = 0
        var tripUpsertCalls = 0
        var tripTombstoneCalls = 0
        var vehicleUpsert: suspend (
            String,
            DriveVehicleEntity,
            String
        ) -> DriveRemoteWriteResult = { _, _, _ -> DriveRemoteWriteResult.Applied }

        override suspend fun upsertVehicle(
            ownerUid: String,
            vehicle: DriveVehicleEntity,
            operationId: String
        ): DriveRemoteWriteResult {
            vehicleUpsertCalls++
            return vehicleUpsert(ownerUid, vehicle, operationId)
        }

        override suspend fun tombstoneVehicle(
            ownerUid: String,
            vehicle: DriveVehicleEntity,
            operationId: String
        ): DriveRemoteWriteResult = DriveRemoteWriteResult.Applied

        override suspend fun upsertTrip(
            ownerUid: String,
            trip: DriveTripEntity,
            operationId: String
        ): DriveRemoteWriteResult {
            tripUpsertCalls++
            return DriveRemoteWriteResult.Applied
        }

        override suspend fun tombstoneTrip(
            ownerUid: String,
            trip: DriveTripEntity,
            operationId: String
        ): DriveRemoteWriteResult {
            tripTombstoneCalls++
            return DriveRemoteWriteResult.Applied
        }
    }

    private class FakeVehicleDao : DriveVehicleDao {
        private val rows = linkedMapOf<String, DriveVehicleEntity>()

        override suspend fun upsert(vehicle: DriveVehicleEntity) {
            rows[key(vehicle.userId, vehicle.id)] = vehicle
        }

        override suspend fun upsertAll(vehicles: List<DriveVehicleEntity>) {
            vehicles.forEach { upsert(it) }
        }

        override fun observeActiveVehicles(userId: String): Flow<List<DriveVehicleEntity>> =
            flowOf(rows.values.filter { it.userId == userId && it.deletedAt == null })

        override fun observeActiveVehicle(
            userId: String,
            id: String
        ): Flow<DriveVehicleEntity?> = flowOf(rows[key(userId, id)]?.takeIf { it.deletedAt == null })

        override suspend fun getVehicle(userId: String, id: String): DriveVehicleEntity? =
            rows[key(userId, id)]

        override suspend fun getActiveVehicle(userId: String, id: String): DriveVehicleEntity? =
            rows[key(userId, id)]?.takeIf { it.deletedAt == null }

        override suspend fun getActiveVehiclesSnapshot(userId: String): List<DriveVehicleEntity> =
            rows.values.filter { it.userId == userId && it.deletedAt == null }

        override suspend fun setAssignmentMirror(
            userId: String,
            vehicleId: String,
            personId: String?
        ): Int {
            val current = rows[key(userId, vehicleId)] ?: return 0
            rows[key(userId, vehicleId)] = current.copy(assignedPersonId = personId)
            return 1
        }

        override fun observeActiveVehiclesWithSummary(
            userId: String
        ): Flow<List<DriveVehicleWithSummary>> = flowOf(emptyList())

        override fun observeActiveVehicleWithSummary(
            userId: String,
            id: String
        ): Flow<DriveVehicleWithSummary?> = flowOf(null)

        override suspend fun getTombstones(userId: String): List<DriveVehicleEntity> =
            rows.values.filter { it.userId == userId && it.deletedAt != null }

        override suspend fun getPendingSyncVehicles(
            userId: String,
            syncStates: List<String>
        ): List<DriveVehicleEntity> = rows.values.filter {
            it.userId == userId && it.syncState in syncStates
        }

        override suspend fun deleteAllForUser(userId: String): Int =
            removeWhere { it.userId == userId }

        override suspend fun deleteAllExceptUser(userId: String): Int =
            removeWhere { it.userId != userId }

        override suspend fun deleteAll(): Int = removeWhere { true }

        private fun removeWhere(predicate: (DriveVehicleEntity) -> Boolean): Int {
            val keys = rows.filterValues(predicate).keys
            keys.forEach(rows::remove)
            return keys.size
        }

        override suspend fun markDeleted(
            userId: String,
            id: String,
            deletedAt: Long,
            updatedAt: Long,
            syncState: String
        ): Int {
            val current = rows[key(userId, id)] ?: return 0
            rows[key(userId, id)] = current.copy(
                deletedAt = current.deletedAt ?: deletedAt,
                updatedAt = maxOf(current.updatedAt, updatedAt),
                syncState = syncState
            )
            return 1
        }

        override suspend fun setSyncStateIfUnchanged(
            userId: String,
            id: String,
            expectedUpdatedAt: Long,
            syncState: String
        ): Int {
            val current = rows[key(userId, id)] ?: return 0
            if (current.updatedAt != expectedUpdatedAt) return 0
            rows[key(userId, id)] = current.copy(syncState = syncState)
            return 1
        }
    }

    private class FakeTripDao : DriveTripDao {
        private val rows = linkedMapOf<String, DriveTripEntity>()

        override suspend fun upsert(trip: DriveTripEntity) {
            rows[key(trip.userId, trip.id)] = trip
        }

        override suspend fun upsertAll(trips: List<DriveTripEntity>) {
            trips.forEach { upsert(it) }
        }

        override fun observeActiveTripsForVehicle(
            userId: String,
            vehicleId: String
        ): Flow<List<DriveTripEntity>> = flowOf(
            rows.values.filter {
                it.userId == userId && it.vehicleId == vehicleId && it.deletedAt == null
            }
        )

        override fun observeActiveTrips(userId: String): Flow<List<DriveTripEntity>> =
            flowOf(rows.values.filter { it.userId == userId && it.deletedAt == null })

        override suspend fun getTripsForVehicle(
            userId: String,
            vehicleId: String
        ): List<DriveTripEntity> = rows.values.filter {
            it.userId == userId && it.vehicleId == vehicleId
        }

        override fun observeActiveTrip(userId: String, id: String): Flow<DriveTripEntity?> =
            flowOf(rows[key(userId, id)]?.takeIf { it.deletedAt == null })

        override suspend fun getTrip(userId: String, id: String): DriveTripEntity? =
            rows[key(userId, id)]

        override suspend fun getActiveTrip(userId: String, id: String): DriveTripEntity? =
            rows[key(userId, id)]?.takeIf { it.deletedAt == null }

        override fun observeSummary(userId: String, vehicleId: String): Flow<DriveTripSummary> =
            flowOf(DriveTripSummary(0.0, 0, null))

        override suspend fun getActiveTripIdsForVehicle(
            userId: String,
            vehicleId: String
        ): List<String> = getTripsForVehicle(userId, vehicleId)
            .filter { it.deletedAt == null }
            .map { it.id }

        override suspend fun getTombstones(userId: String): List<DriveTripEntity> =
            rows.values.filter { it.userId == userId && it.deletedAt != null }

        override suspend fun getPendingSyncTrips(
            userId: String,
            syncStates: List<String>
        ): List<DriveTripEntity> = rows.values.filter {
            it.userId == userId && it.syncState in syncStates
        }

        override suspend fun deleteAllForUser(userId: String): Int =
            removeWhere { it.userId == userId }

        override suspend fun deleteAllExceptUser(userId: String): Int =
            removeWhere { it.userId != userId }

        override suspend fun deleteAll(): Int = removeWhere { true }

        private fun removeWhere(predicate: (DriveTripEntity) -> Boolean): Int {
            val keys = rows.filterValues(predicate).keys
            keys.forEach(rows::remove)
            return keys.size
        }

        override suspend fun markDeleted(
            userId: String,
            id: String,
            deletedAt: Long,
            updatedAt: Long,
            syncState: String
        ): Int {
            val current = rows[key(userId, id)] ?: return 0
            rows[key(userId, id)] = current.copy(
                deletedAt = current.deletedAt ?: deletedAt,
                updatedAt = maxOf(current.updatedAt, updatedAt),
                syncState = syncState
            )
            return 1
        }

        override suspend fun markDeletedForVehicle(
            userId: String,
            vehicleId: String,
            deletedAt: Long,
            updatedAt: Long,
            syncState: String
        ): Int {
            val matches = getTripsForVehicle(userId, vehicleId)
            matches.forEach { markDeleted(userId, it.id, deletedAt, updatedAt, syncState) }
            return matches.size
        }

        override suspend fun setSyncStateIfUnchanged(
            userId: String,
            id: String,
            expectedUpdatedAt: Long,
            syncState: String
        ): Int {
            val current = rows[key(userId, id)] ?: return 0
            if (current.updatedAt != expectedUpdatedAt) return 0
            rows[key(userId, id)] = current.copy(syncState = syncState)
            return 1
        }
    }

    private class FakeOperationDao : DriveSyncOperationDao {
        private val rows = linkedMapOf<String, DriveSyncOperationEntity>()

        override suspend fun upsert(operation: DriveSyncOperationEntity) {
            rows[key(operation.userId, operation.entityType, operation.recordId)] = operation
        }

        override suspend fun getOperation(
            userId: String,
            entityType: String,
            recordId: String
        ): DriveSyncOperationEntity? = rows[key(userId, entityType, recordId)]

        override suspend fun getRetryEligibleOperations(
            userId: String,
            now: Long,
            limit: Int
        ): List<DriveSyncOperationEntity> = rows.values
            .filter {
                it.userId == userId &&
                    it.retryEligible &&
                    (it.nextAttemptAt == null || it.nextAttemptAt <= now)
            }
            .sortedWith(compareBy({ it.createdAt }, { it.updatedAt }, { it.recordId }))
            .take(limit)

        override fun observePendingCount(userId: String): Flow<Int> =
            flowOf(rows.values.count { it.userId == userId })

        override suspend fun pendingCount(userId: String): Int =
            rows.values.count { it.userId == userId }

        override suspend fun deleteForRecord(
            userId: String,
            entityType: String,
            recordId: String
        ): Int = if (rows.remove(key(userId, entityType, recordId)) != null) 1 else 0

        override suspend fun deleteAllForUser(userId: String): Int =
            removeWhere { it.userId == userId }

        override suspend fun deleteAllExceptUser(userId: String): Int =
            removeWhere { it.userId != userId }

        override suspend fun deleteAll(): Int = removeWhere { true }

        private fun removeWhere(predicate: (DriveSyncOperationEntity) -> Boolean): Int {
            val keys = rows.filterValues(predicate).keys
            keys.forEach(rows::remove)
            return keys.size
        }

        override suspend fun recordAttemptIfCurrent(
            userId: String,
            entityType: String,
            recordId: String,
            operationId: String,
            updatedAt: Long,
            lastErrorCode: String?,
            retryEligible: Boolean,
            nextAttemptAt: Long?
        ): Int {
            val rowKey = key(userId, entityType, recordId)
            val current = rows[rowKey] ?: return 0
            if (current.operationId != operationId) return 0
            rows[rowKey] = current.copy(
                updatedAt = updatedAt,
                attemptCount = current.attemptCount + 1,
                lastErrorCode = lastErrorCode,
                retryEligible = retryEligible,
                nextAttemptAt = nextAttemptAt
            )
            return 1
        }

        override suspend fun deleteIfCurrent(
            userId: String,
            entityType: String,
            recordId: String,
            operationId: String
        ): Int {
            val rowKey = key(userId, entityType, recordId)
            val current = rows[rowKey] ?: return 0
            if (current.operationId != operationId) return 0
            rows.remove(rowKey)
            return 1
        }
    }

    private companion object {
        const val USER_ID = "user-a"
        const val VEHICLE_ID = "vehicle-a"
        const val TRIP_ID = "trip-a"

        fun key(vararg parts: String): String = parts.joinToString("|")

        fun vehicle() = DriveVehicleEntity(
            id = VEHICLE_ID,
            userId = USER_ID,
            displayName = "Vehicle",
            createdAt = 100L,
            updatedAt = 100L,
            syncState = DriveSyncState.LOCAL_PENDING.name
        )

        fun operation(type: DriveSyncOperationType) = DriveSyncOperationEntity(
            operationId = "operation-token",
            userId = USER_ID,
            entityType = type.entityType.name,
            recordId = VEHICLE_ID,
            operationType = type.name,
            createdAt = 100L,
            updatedAt = 100L
        )

        fun trip() = DriveTripEntity(
            id = TRIP_ID,
            userId = USER_ID,
            vehicleId = VEHICLE_ID,
            startedAt = 100L,
            distanceKm = 12.5,
            purpose = "PERSONAL",
            entrySource = "MANUAL",
            createdAt = 100L,
            updatedAt = 100L,
            syncState = DriveSyncState.LOCAL_PENDING.name
        )

        fun tripOperation(type: DriveSyncOperationType) = DriveSyncOperationEntity(
            operationId = "trip-operation-token",
            userId = USER_ID,
            entityType = DriveSyncEntityType.TRIP.name,
            recordId = TRIP_ID,
            operationType = type.name,
            createdAt = 100L,
            updatedAt = 100L
        )
    }
}
