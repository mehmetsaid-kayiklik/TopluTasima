package com.example.toplutasima.network.firestore

import android.util.Log
import com.example.toplutasima.model.FavoriteStop
import com.example.toplutasima.model.UsageType
import kotlinx.coroutines.tasks.await

class FirestoreFavoriteDataSource {
    private fun collection() = FirestoreHelper.favoritesCollection()

    suspend fun saveFavorite(fav: FavoriteStop) {
        FirestoreHelper.safeFirestore {
            collection().document(fav.id).set(
                mapOf(
                    "id" to fav.id,
                    "stopId" to fav.stopId,
                    "stopName" to fav.stopName,
                    "label" to fav.label,
                    "usageType" to fav.usageType.name
                )
            ).await()
        }.getOrElse { e ->
            Log.e(TAG, "saveFavorite failed for: ${fav.id}", e)
            throw e
        }
    }

    suspend fun deleteFavorite(favId: String) {
        FirestoreHelper.safeFirestore {
            collection().document(favId).delete().await()
        }.getOrElse { e ->
            Log.e(TAG, "deleteFavorite failed for: $favId", e)
            throw e
        }
    }

    suspend fun fetchAllFavorites(): List<FavoriteStop> {
        return FirestoreHelper.safeFirestore {
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
        }.getOrElse { e ->
            Log.e(TAG, "fetchAllFavorites failed", e)
            throw e
        }
    }

    private companion object {
        private const val TAG = "FirestoreFavoriteDataSource"
    }
}
