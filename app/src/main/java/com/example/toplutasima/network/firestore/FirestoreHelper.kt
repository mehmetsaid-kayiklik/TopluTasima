package com.example.toplutasima.network.firestore

import com.example.toplutasima.auth.CurrentUserProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {

    private val db get() = FirebaseFirestore.getInstance()
    val currentUserId: String
        get() = CurrentUserProvider.requireUserId()

    fun userRoot(userId: String = currentUserId) =
        db.collection("users").document(userId.also { require(it.isNotBlank()) })

    fun tripsCollection(userId: String = currentUserId): CollectionReference =
        userRoot(userId).collection("trips")

    fun favoritesCollection(): CollectionReference =
        userRoot().collection("favorite_stops")

    fun personsCollection(): CollectionReference =
        userRoot().collection("persons")

    fun personalTripsCollection(): CollectionReference =
        userRoot().collection("personaltrips")

    fun batch() = db.batch()

    suspend fun <T> safeFirestore(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
