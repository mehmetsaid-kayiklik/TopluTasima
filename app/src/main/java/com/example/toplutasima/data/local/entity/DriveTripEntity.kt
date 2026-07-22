package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "drive_trips",
    primaryKeys = ["userId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = DriveVehicleEntity::class,
            parentColumns = ["userId", "id"],
            childColumns = ["userId", "vehicleId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["userId", "vehicleId", "deletedAt", "startedAt"]),
        Index(value = ["userId", "syncState", "updatedAt"])
    ]
)
data class DriveTripEntity(
    val id: String,
    val userId: String,
    val vehicleId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val distanceKm: Double,
    val purpose: String,
    val startLocationName: String? = null,
    val endLocationName: String? = null,
    val notes: String? = null,
    val entrySource: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String
)
