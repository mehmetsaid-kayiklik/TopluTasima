package com.example.toplutasima.repository

import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.network.PersonalFirestoreService
import com.example.toplutasima.network.firestore.FirestorePersonService

/**
 * Kişisel araç yolculukları için repository.
 * PersonalFirestoreService üzerinden Firestore ile iletişim kurar.
 * Toplu taşıma kayıt repository'lerinden tamamen bağımsızdır.
 */
class PersonalTripRepository {

    suspend fun saveDraft(trip: PersonalTrip): String =
        PersonalFirestoreService.saveDraft(trip)

    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean =
        PersonalFirestoreService.updateTrip(docId, fields)

    suspend fun deleteTrip(docId: String): Boolean =
        PersonalFirestoreService.deleteTrip(docId)

    suspend fun getAll(): List<PersonalTrip> =
        PersonalFirestoreService.fetchAll()

    suspend fun getForMonth(yearMonth: String): List<PersonalTrip> =
        PersonalFirestoreService.fetchForMonth(yearMonth)

    suspend fun getPersonPlateSuggestions() =
        FirestorePersonService.fetchPlateSuggestions()
}
