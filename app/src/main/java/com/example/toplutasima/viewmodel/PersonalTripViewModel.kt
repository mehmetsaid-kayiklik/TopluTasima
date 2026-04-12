package com.example.toplutasima.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.location.PersonalLocationHelper
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.network.PersonalFirestoreService
import com.example.toplutasima.repository.PersonalTripRepository
import com.example.toplutasima.service.PersonalTripForegroundService
import com.example.toplutasima.ui.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.util.UUID

data class PersonalTripUiState(
    val trips: List<PersonalTrip> = emptyList(),
    val isLoading: Boolean = false,
    val selectedYearMonth: String? = null,     // null = tümü
    val showAddDialog: Boolean = false,
    val editingTrip: PersonalTrip? = null,
    val activeDocId: String? = null,           // Bindim yapılmış ama İndim yapılmamış kaydın docId'si
    val liveDistanceKm: Double = 0.0,          // ORS'ten gelen anlık km
    val isTrackingActive: Boolean = false,
    val isResolvingLocation: Boolean = false,
    val statusMessage: String = "",
    // ── İnline form alanları (toplu taşıma gibi her zaman görünür) ──────────
    val formAracTuru: String = "Otomobil",
    val formPlaka: String = "",
    val formHavaDurumu: String = "Bilinmiyor",
    val formTarih: String = "",               // init sırasında bugünün tarihi set edilir
    val formSurucu: Boolean? = null,
    val formYolcuSayisi: String = "",
    val formNot: String = "",
    val formAracMenuOpen: Boolean = false,
    val formHavaMenuOpen: Boolean = false
)

class PersonalTripViewModel(
    application: Application,
    private val repository: PersonalTripRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        PersonalTripUiState(
            formTarih = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        )
    )
    val uiState: StateFlow<PersonalTripUiState> = _uiState.asStateFlow()

    private val locationHelper = PersonalLocationHelper(application)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    init {
        load()
        // Servis StateFlow'larını gözlemle
        viewModelScope.launch {
            PersonalTripForegroundService.liveDistanceKm.collect { km ->
                _uiState.value = _uiState.value.copy(liveDistanceKm = km)
            }
        }
        viewModelScope.launch {
            PersonalTripForegroundService.isTracking.collect { tracking ->
                _uiState.value = _uiState.value.copy(isTrackingActive = tracking)
            }
        }
    }

    // ── Veri Yükleme ─────────────────────────────────────────────────────────

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val trips = if (_uiState.value.selectedYearMonth != null)
                    repository.getForMonth(_uiState.value.selectedYearMonth!!)
                else
                    repository.getAll()
                _uiState.value = _uiState.value.copy(trips = trips, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Yüklenemedi: ${e.message}"
                )
            }
        }
    }

    fun setMonthFilter(yearMonth: String?) {
        _uiState.value = _uiState.value.copy(selectedYearMonth = yearMonth)
        load()
    }

    // ── İnline Form ──────────────────────────────────────────────────────────

    fun updateFormField(field: String, value: String) {
        _uiState.value = _uiState.value.copy(
            formAracTuru      = if (field == "aracTuru") value else _uiState.value.formAracTuru,
            formPlaka         = if (field == "plaka") value.uppercase() else _uiState.value.formPlaka,
            formHavaDurumu    = if (field == "hava") value else _uiState.value.formHavaDurumu,
            formTarih         = if (field == "tarih") value else _uiState.value.formTarih,
            formYolcuSayisi   = if (field == "yolcuSayisi") value else _uiState.value.formYolcuSayisi,
            formNot           = if (field == "not") value else _uiState.value.formNot,
            formAracMenuOpen  = if (field == "aracTuru") false else if (field == "aracMenu") value == "true" else _uiState.value.formAracMenuOpen,
            formHavaMenuOpen  = if (field == "hava") false else if (field == "havaMenu") value == "true" else _uiState.value.formHavaMenuOpen
        )
    }

    fun updateFormSurucu(value: Boolean?) {
        _uiState.value = _uiState.value.copy(formSurucu = value)
    }

    fun resetForm() {
        _uiState.value = _uiState.value.copy(
            formAracTuru = "Otomobil",
            formPlaka = "",
            formHavaDurumu = "Bilinmiyor",
            formTarih = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            formSurucu = null,
            formYolcuSayisi = "",
            formNot = "",
            formAracMenuOpen = false,
            formHavaMenuOpen = false
        )
    }

    /** İnline formdan Firestore'a taslak kaydeder. */
    fun saveFromInlineForm() {
        val s = _uiState.value
        if (s.formAracTuru.isBlank() || s.formTarih.isBlank()) {
            _uiState.value = s.copy(statusMessage = "Araç türü ve tarih zorunlu")
            return
        }
        viewModelScope.launch {
            try {
                val trip = PersonalTrip(
                    id          = UUID.randomUUID().toString(),
                    tarih       = s.formTarih,
                    aracTuru    = s.formAracTuru,
                    plaka       = s.formPlaka,
                    havaDurumu  = s.formHavaDurumu,
                    surucu      = s.formSurucu,
                    yolcuSayisi = s.formYolcuSayisi.toIntOrNull(),
                    not         = s.formNot
                )
                repository.saveDraft(trip)
                resetForm()
                _uiState.value = _uiState.value.copy(statusMessage = "✅ Kayıt eklendi")
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Kaydedilemedi: ${e.message}")
            }
        }
    }

    // ── Dialog (yalnızca düzenleme için) ──────────────────────────────────────

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingTrip = null)
    }

    fun openEditDialog(trip: PersonalTrip) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingTrip = trip)
    }

    fun closeDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingTrip = null)
    }

    // ── Kayıt (dialog'dan) ────────────────────────────────────────────────────

    /**
     * Yeni taslak kaydı Firestore'a yazar (durum = "beklemede").
     */
    fun saveDraft(
        aracTuru: String,
        plaka: String,
        havaDurumu: String,
        tarih: String,
        surucu: Boolean?,
        yolcuSayisi: Int?,
        not: String
    ) {
        viewModelScope.launch {
            try {
                val trip = PersonalTrip(
                    id       = UUID.randomUUID().toString(),
                    tarih    = tarih,
                    aracTuru = aracTuru,
                    plaka    = plaka.uppercase(),
                    havaDurumu = havaDurumu,
                    surucu   = surucu,
                    yolcuSayisi = yolcuSayisi,
                    not      = not
                )
                repository.saveDraft(trip)
                closeDialog()
                _uiState.value = _uiState.value.copy(statusMessage = "Kayıt eklendi")
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Kaydedilemedi: ${e.message}")
            }
        }
    }

    /**
     * Mevcut kaydı günceller (yalnızca düzenleme modu için).
     */
    fun updateTrip(trip: PersonalTrip) {
        viewModelScope.launch {
            try {
                val fields = mapOf(
                    "aracTuru"    to trip.aracTuru,
                    "plaka"       to trip.plaka.uppercase(),
                    "havaDurumu"  to trip.havaDurumu,
                    "surucu"      to trip.surucu,
                    "yolcuSayisi" to trip.yolcuSayisi,
                    "not"         to trip.not
                )
                repository.updateTrip(trip.firestoreDocId, fields)
                closeDialog()
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Güncellenemedi: ${e.message}")
            }
        }
    }

    fun deleteTrip(docId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTrip(docId)
                if (_uiState.value.activeDocId == docId) {
                    stopTracking(getApplication())
                }
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Silinemedi: ${e.message}")
            }
        }
    }

    // ── Bindim ────────────────────────────────────────────────────────────────

    /**
     * "Bindim" tuşuna basıldığında:
     *  1. Şu anki saat alınır.
     *  2. GPS → Geocoder → kalkış sokak adresi alınır.
     *  3. Firestore güncellenir: { durum: "aktif", kaldigiSaat, kaldigiYer, kaldigiLat, kaldigiLng }
     *  4. Foreground Service başlatılır (waypoint takibi + ORS).
     */
    fun recordBindim(context: Context, docId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = true,
                statusMessage = "Konum alınıyor..."
            )
            val now = LocalTime.now().format(timeFormatter)
            val location = locationHelper.resolveCurrentLocation()
            val fields = mutableMapOf<String, Any?>(
                "kaldigiSaat" to now,
                "durum"       to PersonalTrip.DURUM_AKTIF
            )
            if (location != null) {
                fields["kaldigiYer"] = location.first
                fields["kaldigiLat"] = location.second
                fields["kaldigiLng"] = location.third
            }
            repository.updateTrip(docId, fields)
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = false,
                activeDocId = docId,
                statusMessage = if (location != null)
                    "Biniş kaydedildi — ${location.first}"
                else
                    "Biniş kaydedildi (konum alınamadı)"
            )
            // Foreground Service'i başlat
            val intent = Intent(context, PersonalTripForegroundService::class.java)
                .setAction(PersonalTripForegroundService.ACTION_START)
            context.startForegroundService(intent)
            load()
        }
    }

    // ── İndim ─────────────────────────────────────────────────────────────────

    /**
     * "İndim" tuşuna basıldığında:
     *  1. Foreground Service durdurulur (son ORS batch gönderilir).
     *  2. Şu anki saat alınır.
     *  3. GPS → Geocoder → varış sokak adresi alınır.
     *  4. Firestore: { durum: "tamamlandi", varisSaat, varisYeri, mesafe, yolSuresi }
     */
    fun recordIndim(context: Context, docId: String) {
        viewModelScope.launch {
            // Önce servisi durdur (son batch ORS'e gönderilecek)
            stopTracking(context)
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = true,
                statusMessage = "Konum alınıyor..."
            )
            val now = LocalTime.now().format(timeFormatter)
            val location = locationHelper.resolveCurrentLocation()

            // Servisten son mesafeyi oku (stopTracking sonrası StateFlow güncelleniyor)
            val km = _uiState.value.liveDistanceKm
            val mesafe = if (km > 0.0) String.format("%.1f km", km) else ""

            // Bindim saatini bul (yolSuresi hesabı için)
            val activeTrip = _uiState.value.trips.find { it.firestoreDocId == docId }
            val yolSuresi = PersonalFirestoreService.computeYolSuresi(
                activeTrip?.kaldigiSaat ?: "", now
            )

            val fields = mutableMapOf<String, Any?>(
                "varisSaat" to now,
                "mesafe"    to mesafe,
                "yolSuresi" to yolSuresi,
                "durum"     to PersonalTrip.DURUM_TAMAMLANDI
            )
            if (location != null) {
                fields["varisYeri"] = location.first
                fields["varisLat"]  = location.second
                fields["varisLng"]  = location.third
            }
            repository.updateTrip(docId, fields)
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = false,
                activeDocId = null,
                liveDistanceKm = 0.0,
                statusMessage = if (mesafe.isNotBlank()) "Yolculuk tamamlandı — $mesafe"
                               else "Yolculuk tamamlandı"
            )
            load()
        }
    }

    private fun stopTracking(context: Context) {
        val intent = Intent(context, PersonalTripForegroundService::class.java)
            .setAction(PersonalTripForegroundService.ACTION_STOP)
        context.startService(intent)
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = "")
    }

    fun hasLocationPermission() = locationHelper.hasPermission()
}
