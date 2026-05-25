package com.example.toplutasima.network.firestore

import com.example.toplutasima.auth.AuthService
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

object FirestorePersonService {

    private fun collection() = FirebaseFirestore.getInstance()
        .collection("users")
        .document(AuthService.uid)
        .collection("persons")

    /** Room'daki ProfileEntity'yi Firestore'a yazar/günceller. */
    suspend fun upsertPerson(profile: ProfileEntity): Boolean {
        return try {
            val data = mapOf(
                "id"                to profile.id,
                "displayName"       to profile.displayName,
                "nameKind"          to profile.nameKind,
                "memoryNote"        to profile.memoryNote,
                "birthHint"         to profile.birthHint,
                "infoSource"        to profile.infoSource,
                "createdAt"         to profile.createdAt,
                "updatedAt"         to profile.updatedAt,
                "archived"          to profile.archived,
                "sharedWithTransit" to profile.sharedWithTransit
            )
            collection().document(profile.id).set(data).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { false }
    }

    /** Belirli bir kişiyi siler. */
    suspend fun deletePerson(profileId: String): Boolean {
        return try {
            collection().document(profileId).delete().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { false }
    }

    /**
     * Sadece sharedWithTransit = true olan kişileri çeker.
     * TopluTaşıma'da "yolcu seç" listesi için kullanılır.
     */
    suspend fun fetchSharedWithTransit(): List<ProfileEntity> {
        val snap = collection()
            .whereEqualTo("sharedWithTransit", true)
            .whereEqualTo("archived", false)
            .get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            ProfileEntity(
                id                = d["id"]?.toString() ?: doc.id,
                displayName       = d["displayName"]?.toString() ?: "",
                nameKind          = d["nameKind"]?.toString() ?: "UNKNOWN",
                memoryNote        = d["memoryNote"]?.toString(),
                birthHint         = d["birthHint"]?.toString(),
                infoSource        = d["infoSource"]?.toString() ?: "UNKNOWN",
                createdAt         = (d["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt         = (d["updatedAt"] as? Number)?.toLong() ?: 0L,
                archived          = d["archived"] as? Boolean ?: false,
                sharedWithTransit = true
            )
        }
    }

    /** Tüm kişileri çeker. */
    suspend fun fetchAll(): List<Map<String, Any>> {
        val snap = collection().get().await()
        return snap.documents.mapNotNull { it.data }
    }
}
