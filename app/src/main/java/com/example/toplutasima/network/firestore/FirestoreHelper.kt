package com.example.toplutasima.network.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {

    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
        ?: error("Kullanici oturumu yok")

    fun userRoot() = db.collection("users").document(uid)

    fun tripsCollection(): CollectionReference =
        userRoot().collection("trips")

    fun favoritesCollection(): CollectionReference =
        userRoot().collection("favorites")

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
