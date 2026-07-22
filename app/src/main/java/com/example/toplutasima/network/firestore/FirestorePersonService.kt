package com.example.toplutasima.network.firestore

import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.model.PersonPlateSuggestion
import com.example.toplutasima.model.PlateCountries
import kotlinx.coroutines.tasks.await

object FirestorePersonService {
    data class PersonShareState(
        val id: String,
        val sharedWithTransit: Boolean,
        val archived: Boolean,
        val documentId: String = id,
        val payloadId: String? = id,
        val identityValid: Boolean = documentId == payloadId
    )

    data class PersonTombstone(
        val personId: String,
        val deletedAt: Long
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
            val payloadId = d["id"]?.toString() ?: return@mapNotNull null
            if (payloadId != doc.id) return@mapNotNull null
            ProfileEntity(
                id                = doc.id,
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
            val payloadId = d["id"]?.toString()
            PersonShareState(
                id = doc.id,
                sharedWithTransit = d["sharedWithTransit"] as? Boolean ?: false,
                archived = d["archived"] as? Boolean ?: false,
                documentId = doc.id,
                payloadId = payloadId,
                identityValid = payloadId == doc.id
            )
        }
    }

    suspend fun fetchPersonTombstones(): List<PersonTombstone> {
        val snap = FirestoreHelper.userRoot().collection("sync_tombstones")
            .whereEqualTo("entityType", "person")
            .get().await()
        return snap.documents.mapNotNull { doc ->
            val personId = doc.getString("entityId") ?: return@mapNotNull null
            val deletedAt = doc.getLong("deletedAt") ?: return@mapNotNull null
            PersonTombstone(personId = personId, deletedAt = deletedAt)
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
