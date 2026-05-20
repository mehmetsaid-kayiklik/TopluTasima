package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity

@Dao
interface TripProfileLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: TripProfileLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<TripProfileLinkEntity>)

    @Query("SELECT * FROM trip_profile_links")
    suspend fun getAllLinks(): List<TripProfileLinkEntity>

    @Query("SELECT * FROM trip_profile_links WHERE profileId = :profileId")
    suspend fun getLinksForProfile(profileId: String): List<TripProfileLinkEntity>

    @Query("SELECT * FROM trip_profile_links WHERE tripStableKey = :tripStableKey")
    suspend fun getLinksForTrip(tripStableKey: String): List<TripProfileLinkEntity>

    @Query("UPDATE trip_profile_links SET tripStableKey = :newStableKey, updatedAt = :updatedAt WHERE tripStableKey = :oldStableKey")
    suspend fun updateStableKey(oldStableKey: String, newStableKey: String, updatedAt: Long)

    @Query("DELETE FROM trip_profile_links WHERE tripStableKey = :tripId OR tripStableKey = :firestoreDocId")
    suspend fun deleteLinksForTrip(tripId: String, firestoreDocId: String)

    @Query("DELETE FROM trip_profile_links")
    suspend fun deleteAllLinks()
}
