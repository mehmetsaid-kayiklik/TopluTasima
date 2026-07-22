package com.example.toplutasima.drive

import com.example.toplutasima.BuildConfig

/** Build-time rollback switch for the isolated vehicle/manual-drive domain. */
object DriveFeatureFlags {
    const val DRIVE_CORE: Boolean = true
    val DRIVE_PERSON_DIRECTORY: Boolean = BuildConfig.DRIVE_PERSON_DIRECTORY
}
