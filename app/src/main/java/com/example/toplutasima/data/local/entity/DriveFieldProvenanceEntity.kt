package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_field_provenance",
    primaryKeys = ["userId", "entityType", "recordId", "fieldName"],
    indices = [Index(value = ["userId", "entityType", "recordId"])]
)
data class DriveFieldProvenanceEntity(
    val userId: String,
    val entityType: String,
    val recordId: String,
    val fieldName: String,
    val source: String,
    val updatedAt: Long
)
