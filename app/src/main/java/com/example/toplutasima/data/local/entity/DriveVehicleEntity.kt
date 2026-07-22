package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_vehicles",
    primaryKeys = ["userId", "id"],
    indices = [
        Index(value = ["userId", "deletedAt", "displayName"]),
        Index(value = ["userId", "syncState", "updatedAt"])
    ]
)
data class DriveVehicleEntity(
    val id: String,
    val userId: String,
    val displayName: String,
    val brand: String? = null,
    val model: String? = null,
    val licensePlate: String? = null,
    val modelYear: Int? = null,
    val fuelType: String? = null,
    val initialOdometerKm: Double? = null,
    val currentOdometerKm: Double? = null,
    val assignedPersonId: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: String
)
