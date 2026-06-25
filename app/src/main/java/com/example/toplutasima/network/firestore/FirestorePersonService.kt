package com.example.toplutasima.network.firestore

import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.model.PersonPlateSuggestion
import com.example.toplutasima.model.PlateCountries
import kotlinx.coroutines.tasks.await

object FirestorePersonService {
    data class PersonShareState(
        val id: String,
        val sharedWithTransit: Boolean,
        val archived: Boolean
    )

    private fun collection() = FirestoreHelper.personsCollection()

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
                memoryNote        = d["memoryNote"]?.toString(),
                createdAt         = (d["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt         = (d["updatedAt"] as? Number)?.toLong() ?: 0L,
                archived          = d["archived"] as? Boolean ?: false,
                sharedWithTransit = true
            )
        }
    }

    suspend fun fetchShareStates(): List<PersonShareState> {
        val snap = collection().get().await()
        return snap.documents.map { doc ->
            val d = doc.data.orEmpty()
            PersonShareState(
                id = d["id"]?.toString() ?: doc.id,
                sharedWithTransit = d["sharedWithTransit"] as? Boolean ?: false,
                archived = d["archived"] as? Boolean ?: false
            )
        }
    }

    /** Tüm kişileri çeker. */
    suspend fun fetchPlateSuggestions(): List<PersonPlateSuggestion> {
        val snap = collection().get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data.orEmpty()
            if (d["archived"] as? Boolean == true) return@mapNotNull null
            val plate = d["plaka"]?.toString()?.trim()?.uppercase().orEmpty()
            if (plate.isBlank()) return@mapNotNull null
            PersonPlateSuggestion(
                personId = d["id"]?.toString() ?: doc.id,
                displayName = d["displayName"]?.toString().orEmpty(),
                plaka = plate,
                plakaUlkesi = PlateCountries.normalize(d["plakaUlkesi"]?.toString())
            )
        }.distinctBy { "${it.plaka}|${it.normalizedCountry}" }
    }

    suspend fun fetchAll(): List<Map<String, Any>> {
        val snap = collection().get().await()
        return snap.documents.mapNotNull { it.data }
    }
}
