package com.example.toplutasima.network

import com.example.toplutasima.model.PersonalTrip
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore CRUD servisi — yalnızca "personaltrips" koleksiyonu.
 * "trips" koleksiyonuna hiç dokunmaz.
 */
object PersonalFirestoreService {

    private val db get() = FirebaseFirestore.getInstance()
    private const val COLLECTION = "personaltrips"

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    fun computeSortDate(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        return "${parts[2].padStart(4,'0')}-${parts[1].padStart(2,'0')}-${parts[0].padStart(2,'0')}"
    }

    fun computeYearMonth(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        return "${parts[2].padStart(4,'0')}-${parts[1].padStart(2,'0')}"
    }

    // ── Map → PersonalTrip dönüştürücü ──────────────────────────────────────

    private fun Map<String, Any>.toPersonalTrip(docId: String): PersonalTrip {
        fun str(k: String, def: String = "") = this[k]?.toString() ?: def
        fun dbl(k: String): Double? = (this[k] as? Number)?.toDouble()
        fun bool(k: String): Boolean? = this[k] as? Boolean
        fun int(k: String): Int? = (this[k] as? Number)?.toInt()
        return PersonalTrip(
            id               = str("id"),
            firestoreDocId   = docId,
            tarih            = str("tarih"),
            aracTuru         = str("aracTuru"),
            plaka            = str("plaka"),
            havaDurumu       = str("havaDurumu", "Bilinmiyor"),
            kaldigiYer       = str("kaldigiYer"),
            kaldigiLat       = dbl("kaldigiLat"),
            kaldigiLng       = dbl("kaldigiLng"),
            kaldigiSaat      = str("kaldigiSaat"),
            varisYeri        = str("varisYeri"),
            varisLat         = dbl("varisLat"),
            varisLng         = dbl("varisLng"),
            varisSaat        = str("varisSaat"),
            mesafe           = str("mesafe"),
            yolSuresi        = str("yolSuresi"),
            surucu           = bool("surucu"),
            yolcuSayisi      = int("yolcuSayisi"),
            not              = str("not"),
            durum            = str("durum", PersonalTrip.DURUM_BEKLEMEDE),
            sortDate         = str("sortDate"),
            yearMonth        = str("yearMonth"),
            createdAt        = (this["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    /**
     * Yeni kişisel biniş kaydeder (durum = "beklemede").
     * Dönen değer: Firestore doküman ID'si.
     */
    suspend fun saveDraft(trip: PersonalTrip): String {
        val now = System.currentTimeMillis()
        val data = linkedMapOf<String, Any?>(
            "id"          to trip.id,
            "tarih"       to trip.tarih,
            "aracTuru"    to trip.aracTuru,
            "plaka"       to trip.plaka,
            "havaDurumu"  to trip.havaDurumu,
            "surucu"      to trip.surucu,
            "yolcuSayisi" to trip.yolcuSayisi,
            "not"         to trip.not,
            "durum"       to PersonalTrip.DURUM_BEKLEMEDE,
            "sortDate"    to computeSortDate(trip.tarih),
            "yearMonth"   to computeYearMonth(trip.tarih),
            "createdAt"   to now
        )
        val doc = db.collection(COLLECTION).add(data).await()
        return doc.id
    }

    /**
     * Belirli alanları günceller (Bindim / İndim sonrası).
     */
    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean {
        return try {
            val clean = fields.filterValues { it != null }.mapValues { it.value!! }.toMutableMap()
            val tarih = clean["tarih"]?.toString()
            if (!tarih.isNullOrBlank()) {
                clean["sortDate"] = computeSortDate(tarih)
                clean["yearMonth"] = computeYearMonth(tarih)
            }
            db.collection(COLLECTION).document(docId).update(clean).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { false }
    }

    /**
     * Firestore dokümanını siler.
     */
    suspend fun deleteTrip(docId: String): Boolean {
        return try {
            db.collection(COLLECTION).document(docId).delete().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { false }
    }

    /**
     * Tüm kişisel binişleri çeker — createdAt'e göre azalan sırayla.
     */
    suspend fun fetchAll(): List<PersonalTrip> {
        val snap = db.collection(COLLECTION).get().await()
        return snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data.toPersonalTrip(doc.id)
        }.sortedByDescending { it.createdAt }
    }

    /**
     * Belirli bir aya ait binişleri çeker ("YYYY-MM" formatında).
     */
    suspend fun fetchForMonth(yearMonth: String): List<PersonalTrip> {
        val snap = db.collection(COLLECTION)
            .whereEqualTo("yearMonth", yearMonth)
            .get().await()
        return snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data.toPersonalTrip(doc.id)
        }.sortedByDescending { it.createdAt }
    }
}
