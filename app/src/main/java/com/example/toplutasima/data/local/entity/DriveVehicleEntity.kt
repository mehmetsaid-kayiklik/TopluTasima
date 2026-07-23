package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

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
    val syncState: String,
    val countryCode: String? = null,
    val transmissionType: String? = null,
    val bodyType: String? = null,
    val color: String? = null,
    val vin: String? = null,
    val engineDisplacementCc: Int? = null,
    val enginePowerKw: Int? = null,
    val purchaseDate: Long? = null,
    val purchasePriceMinor: Long? = null,
    val currencyCode: String? = null,
    val primaryPhotoId: String? = null,
    val trimLevel: String? = null,
    val engineCode: String? = null,
    val registrationDate: Long? = null,
    val inspectionDueDate: Long? = null,
    val insuranceDueDate: Long? = null,
    val tireSize: String? = null,
    @ColumnInfo(defaultValue = "2") val schemaVersion: Int = 2,
    @ColumnInfo(defaultValue = "0") val primaryPhotoRevision: Long = 0L,
    val primaryPhotoOperationId: String? = null
)
