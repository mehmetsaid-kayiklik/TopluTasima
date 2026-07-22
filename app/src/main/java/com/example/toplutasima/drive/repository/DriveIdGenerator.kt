package com.example.toplutasima.drive.repository

fun interface DriveIdGenerator {
    fun newId(): String

    companion object {
        val UUID: DriveIdGenerator = DriveIdGenerator { java.util.UUID.randomUUID().toString() }
    }
}
