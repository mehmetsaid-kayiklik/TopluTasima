package com.example.toplutasima.drive.sync

import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

internal data class DriveClassifiedFailure(
    val code: DriveSyncFailureCode,
    val retryable: Boolean
)

internal fun classifyDriveSyncFailure(error: Exception): DriveClassifiedFailure = when (error) {
    is IllegalArgumentException -> DriveClassifiedFailure(
        code = DriveSyncFailureCode.INVALID_DATA,
        retryable = false
    )
    is IOException -> DriveClassifiedFailure(
        code = DriveSyncFailureCode.NETWORK,
        retryable = true
    )
    is FirebaseFirestoreException -> classifyFirestoreFailure(error.code.name)
    else -> DriveClassifiedFailure(
        code = DriveSyncFailureCode.UNKNOWN,
        retryable = false
    )
}

private fun classifyFirestoreFailure(codeName: String): DriveClassifiedFailure = when (codeName) {
    "ABORTED",
    "CANCELLED",
    "DEADLINE_EXCEEDED",
    "INTERNAL",
    "RESOURCE_EXHAUSTED",
    "UNAUTHENTICATED",
    "UNAVAILABLE",
    "UNKNOWN" -> DriveClassifiedFailure(
        code = when (codeName) {
            "RESOURCE_EXHAUSTED" -> DriveSyncFailureCode.RATE_LIMITED
            "UNAVAILABLE" -> DriveSyncFailureCode.SERVICE_UNAVAILABLE
            "UNAUTHENTICATED" -> DriveSyncFailureCode.AUTH_CHANGED
            else -> DriveSyncFailureCode.NETWORK
        },
        retryable = true
    )
    "PERMISSION_DENIED" -> DriveClassifiedFailure(
        code = DriveSyncFailureCode.PERMISSION_DENIED,
        retryable = false
    )
    else -> DriveClassifiedFailure(
        code = DriveSyncFailureCode.INVALID_DATA,
        retryable = false
    )
}
