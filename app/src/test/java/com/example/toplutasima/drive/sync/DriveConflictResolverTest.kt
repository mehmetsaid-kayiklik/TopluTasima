package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveConflictResolverTest {
    @Test
    fun `remote create hydrates a synced local entity`() {
        val resolution = DriveConflictResolver.resolveVehicle(
            local = null,
            remote = vehicle(name = "Remote"),
            pendingOperation = null,
            localProvenance = emptyMap()
        )
        assertEquals("Remote", resolution.entity.displayName)
        assertEquals(DriveSyncState.SYNCED.name, resolution.entity.syncState)
        assertTrue(resolution.provenance.values.all { it == DriveFieldSource.REMOTE })
    }

    @Test
    fun `remote update wins when there is no local pending operation`() {
        val resolution = DriveConflictResolver.resolveVehicle(
            local = vehicle(name = "Old"),
            remote = vehicle(name = "Remote").copy(updatedAt = 200L),
            pendingOperation = null,
            localProvenance = mapOf("displayName" to DriveFieldSource.LOCAL)
        )
        assertEquals("Remote", resolution.entity.displayName)
        assertTrue(resolution.clearPendingOperation)
    }

    @Test
    fun `pending local fields merge with remote fields deterministically`() {
        val local = vehicle(name = "Local").copy(brand = "Old brand")
        val remote = vehicle(name = "Remote").copy(brand = "Remote brand", updatedAt = 200L)
        val resolution = DriveConflictResolver.resolveVehicle(
            local = local,
            remote = remote,
            pendingOperation = operation(DriveSyncOperationType.UPDATE_VEHICLE),
            localProvenance = mapOf(
                "displayName" to DriveFieldSource.LOCAL,
                "brand" to DriveFieldSource.REMOTE
            )
        )
        assertEquals("Local", resolution.entity.displayName)
        assertEquals("Remote brand", resolution.entity.brand)
        assertEquals(DriveFieldSource.MERGED, resolution.provenance["displayName"])
        assertFalse(resolution.clearPendingOperation)
    }

    @Test
    fun `remote delete wins over offline update`() {
        val resolution = DriveConflictResolver.resolveVehicle(
            local = vehicle(name = "Offline edit"),
            remote = vehicle(name = "").copy(deletedAt = 300L, updatedAt = 300L),
            pendingOperation = operation(DriveSyncOperationType.UPDATE_VEHICLE),
            localProvenance = mapOf("displayName" to DriveFieldSource.LOCAL)
        )
        assertEquals(300L, resolution.entity.deletedAt)
        assertEquals(DriveSyncState.SYNCED.name, resolution.entity.syncState)
        assertTrue(resolution.clearPendingOperation)
    }

    @Test
    fun `local delete wins over remote restore`() {
        val local = vehicle(name = "Deleted").copy(
            deletedAt = 250L,
            updatedAt = 250L,
            syncState = DriveSyncState.SYNCED.name
        )
        val resolution = DriveConflictResolver.resolveVehicle(
            local = local,
            remote = vehicle(name = "Restored").copy(updatedAt = 300L),
            pendingOperation = null,
            localProvenance = emptyMap()
        )
        assertEquals(250L, resolution.entity.deletedAt)
        assertFalse(resolution.clearPendingOperation)
    }

    @Test
    fun `trip merge never replaces local actual fields with unrelated remote values`() {
        val local = trip().copy(distanceKm = 12.5)
        val remote = trip().copy(distanceKm = 99.0, purpose = "BUSINESS", updatedAt = 200L)
        val resolution = DriveConflictResolver.resolveTrip(
            local,
            remote,
            operation(DriveSyncOperationType.UPDATE_DRIVE_TRIP, entity = "TRIP"),
            mapOf(
                "distanceKm" to DriveFieldSource.LOCAL,
                "purpose" to DriveFieldSource.REMOTE
            )
        )
        assertEquals(12.5, resolution.entity.distanceKm, 0.0)
        assertEquals("BUSINESS", resolution.entity.purpose)
    }

    private fun vehicle(name: String) = DriveVehicleEntity(
        id = "vehicle",
        userId = "owner",
        displayName = name,
        createdAt = 100L,
        updatedAt = 100L,
        syncState = DriveSyncState.SYNCED.name
    )

    private fun trip() = DriveTripEntity(
        id = "trip",
        userId = "owner",
        vehicleId = "vehicle",
        startedAt = 100L,
        distanceKm = 5.0,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = 100L,
        updatedAt = 100L,
        syncState = DriveSyncState.SYNCED.name
    )

    private fun operation(
        type: DriveSyncOperationType,
        entity: String = type.entityType.name
    ) = DriveSyncOperationEntity(
        operationId = "operation",
        userId = "owner",
        entityType = entity,
        recordId = if (entity == "TRIP") "trip" else "vehicle",
        operationType = type.name,
        createdAt = 100L,
        updatedAt = 100L
    )
}
