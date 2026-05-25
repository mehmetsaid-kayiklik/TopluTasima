package com.example.toplutasima.network.firestore

import android.util.Log
import com.example.toplutasima.auth.AuthService
import com.example.toplutasima.model.FavoriteStop
import com.example.toplutasima.model.UsageType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreFavoriteDataSource(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val favoriteCollectionName: String = "favorite_stops"
) {
    private fun collection() = db
        .collection("users")
        .document(AuthService.uid)
        .collection(favoriteCollectionName)

    suspend fun saveFavorite(fav: FavoriteStop) {
        try {
            collection().document(fav.id).set(
                mapOf(
                    "id" to fav.id,
                    "stopId" to fav.stopId,
                    "stopName" to fav.stopName,
                    "label" to fav.label,
                    "usageType" to fav.usageType.name
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "saveFavorite failed for: ${fav.id}", e)
            throw e
        }
    }

    suspend fun deleteFavorite(favId: String) {
        try {
            collection().document(favId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "deleteFavorite failed for: $favId", e)
            throw e
        }
    }

    suspend fun fetchAllFavorites(): List<FavoriteStop> {
        return try {
            val snapshot = collection().get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val id = data["id"]?.toString() ?: return@mapNotNull null
                val stopId = data["stopId"]?.toString() ?: return@mapNotNull null
                val stopName = data["stopName"]?.toString() ?: return@mapNotNull null
                val label = data["label"]?.toString() ?: stopName
                val usageType = try {
                    UsageType.valueOf(data["usageType"]?.toString() ?: "BOTH")
                } catch (e: Exception) {
                    Log.e(TAG, "usageType parse failed for doc: ${doc.id}", e)
                    UsageType.BOTH
                }
                FavoriteStop(
                    id = id,
                    stopId = stopId,
                    stopName = stopName,
                    label = label,
                    usageType = usageType
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllFavorites failed", e)
            throw e
        }
    }

    private companion object {
        private const val TAG = "FirestoreFavoriteDataSource"
    }
}
