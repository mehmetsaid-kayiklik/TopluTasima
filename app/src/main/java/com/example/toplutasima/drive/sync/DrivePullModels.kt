package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity

data class DriveRemoteCursor(
    val seconds: Long,
    val nanoseconds: Int,
    val documentId: String
) : Comparable<DriveRemoteCursor> {
    init {
        require(nanoseconds in 0..999_999_999) { "Invalid drive cursor" }
        require(documentId.isNotBlank()) { "Drive cursor document ID must not be blank" }
    }

    override fun compareTo(other: DriveRemoteCursor): Int =
        compareValuesBy(this, other, DriveRemoteCursor::seconds,
            DriveRemoteCursor::nanoseconds, DriveRemoteCursor::documentId)
}

data class DriveRemoteVehicle(
    val entity: DriveVehicleEntity,
    val cursor: DriveRemoteCursor?
)

data class DriveRemoteTrip(
    val entity: DriveTripEntity,
    val cursor: DriveRemoteCursor?
)

data class DriveRemotePullBatch(
    val vehicles: List<DriveRemoteVehicle>,
    val trips: List<DriveRemoteTrip>,
    val vehicleCursor: DriveRemoteCursor?,
    val tripCursor: DriveRemoteCursor?
) {
    companion object {
        val EMPTY = DriveRemotePullBatch(emptyList(), emptyList(), null, null)
    }
}

enum class DrivePullMode {
    INITIAL,
    INCREMENTAL
}

data class DrivePullRunResult(
    val pulledCount: Int,
    val retryRequired: Boolean = false,
    val permanentFailureCount: Int = 0
)

internal fun interface DrivePullCoordinator {
    suspend fun pull(ownerUid: String): DrivePullRunResult

    companion object {
        val NO_OP = DrivePullCoordinator { DrivePullRunResult(pulledCount = 0) }
    }
}
