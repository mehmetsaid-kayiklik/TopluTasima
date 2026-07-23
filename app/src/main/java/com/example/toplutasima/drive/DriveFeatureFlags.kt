package com.example.toplutasima.drive

import com.example.toplutasima.BuildConfig

/** Build-time rollback switch for the isolated vehicle/manual-drive domain. */
object DriveFeatureFlags {
    const val DRIVE_CORE: Boolean = true
    val DRIVE_PERSON_DIRECTORY: Boolean = BuildConfig.DRIVE_PERSON_DIRECTORY
    val DRIVE_VEHICLE_PHOTOS: Boolean = BuildConfig.DRIVE_VEHICLE_PHOTOS
    val DRIVE_EXTENDED_VEHICLE_PROFILE: Boolean = BuildConfig.DRIVE_EXTENDED_VEHICLE_PROFILE
    val DRIVE_VEHICLE_LEDGER: Boolean = BuildConfig.DRIVE_VEHICLE_LEDGER
}
