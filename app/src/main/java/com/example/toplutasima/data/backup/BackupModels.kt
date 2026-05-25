package com.example.toplutasima.data.backup

import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBackupModel(
    val id: String,
    val displayName: String,
    val nameKind: String,
    val memoryNote: String? = null,
    val birthHint: String? = null,
    val infoSource: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    val sharedWithTransit: Boolean = false
)

@Serializable
data class TripProfileLinkBackupModel(
    val id: String,
    val tripStableKey: String,
    val profileId: String,
    val seatmateNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val profiles: List<ProfileBackupModel>,
    val tripProfileLinks: List<TripProfileLinkBackupModel>
)

fun ProfileEntity.toBackupModel() = ProfileBackupModel(
    id = id,
    displayName = displayName,
    nameKind = nameKind,
    memoryNote = memoryNote,
    birthHint = birthHint,
    infoSource = infoSource,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    sharedWithTransit = sharedWithTransit
)

fun ProfileBackupModel.toEntity() = ProfileEntity(
    id = id,
    displayName = displayName,
    nameKind = nameKind,
    memoryNote = memoryNote,
    birthHint = birthHint,
    infoSource = infoSource,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    sharedWithTransit = sharedWithTransit
)

fun TripProfileLinkEntity.toBackupModel() = TripProfileLinkBackupModel(
    id = id,
    tripStableKey = tripStableKey,
    profileId = profileId,
    seatmateNote = seatmateNote,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TripProfileLinkBackupModel.toEntity() = TripProfileLinkEntity(
    id = id,
    tripStableKey = tripStableKey,
    profileId = profileId,
    seatmateNote = seatmateNote,
    createdAt = createdAt,
    updatedAt = updatedAt
)
