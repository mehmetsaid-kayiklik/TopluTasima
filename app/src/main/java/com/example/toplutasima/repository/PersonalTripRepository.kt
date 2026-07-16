package com.example.toplutasima.repository

import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.model.PersonPlateSuggestion
import com.example.toplutasima.network.PersonalFirestoreService
import com.example.toplutasima.network.firestore.FirestorePersonService

internal interface PersonalTripFirestoreOperations {
    suspend fun saveDraft(trip: PersonalTrip): String
    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean
    suspend fun deleteTrip(docId: String): Boolean
    suspend fun fetchAll(): List<PersonalTrip>
    suspend fun fetchForMonth(yearMonth: String): List<PersonalTrip>
    suspend fun fetchPlateSuggestions(): List<PersonPlateSuggestion>
}

private object DefaultPersonalTripFirestoreOperations : PersonalTripFirestoreOperations {
    override suspend fun saveDraft(trip: PersonalTrip): String =
        PersonalFirestoreService.saveDraft(trip)

    override suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean =
        PersonalFirestoreService.updateTrip(docId, fields)

    override suspend fun deleteTrip(docId: String): Boolean =
        PersonalFirestoreService.deleteTrip(docId)

    override suspend fun fetchAll(): List<PersonalTrip> =
        PersonalFirestoreService.fetchAll()

    override suspend fun fetchForMonth(yearMonth: String): List<PersonalTrip> =
        PersonalFirestoreService.fetchForMonth(yearMonth)

    override suspend fun fetchPlateSuggestions(): List<PersonPlateSuggestion> =
        FirestorePersonService.fetchPlateSuggestions()
}

/**
 * Kişisel araç yolculukları için repository.
 * PersonalFirestoreService üzerinden Firestore ile iletişim kurar.
 * Toplu taşıma kayıt repository'lerinden tamamen bağımsızdır.
 */
class PersonalTripRepository internal constructor(
    private val firestore: PersonalTripFirestoreOperations
) {

    constructor() : this(DefaultPersonalTripFirestoreOperations)

    suspend fun saveDraft(trip: PersonalTrip): String =
        firestore.saveDraft(trip)

    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean =
        firestore.updateTrip(docId, fields)

    suspend fun deleteTrip(docId: String): Boolean =
        firestore.deleteTrip(docId)

    suspend fun getAll(): List<PersonalTrip> =
        firestore.fetchAll()

    suspend fun getForMonth(yearMonth: String): List<PersonalTrip> =
        firestore.fetchForMonth(yearMonth)

    suspend fun getPersonPlateSuggestions() =
        firestore.fetchPlateSuggestions()
}
