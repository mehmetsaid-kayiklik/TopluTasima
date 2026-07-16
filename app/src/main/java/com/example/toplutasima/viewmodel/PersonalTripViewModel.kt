package com.example.toplutasima.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.model.PersonPlateSuggestion
import com.example.toplutasima.model.PlateCountries
import com.example.toplutasima.repository.PersonalTripRepository
import com.example.toplutasima.service.PersonalTripForegroundService
import com.example.toplutasima.service.PersonalTripPermissionGuard
import com.example.toplutasima.service.PersonalTripTrackingState
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.viewmodel.personaltrip.AndroidPersonalTripRuntime
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripRuntime
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class PersonalTripViewModel internal constructor(
    application: Application,
    private val repository: PersonalTripRepository,
    private val runtime: PersonalTripRuntime
) : AndroidViewModel(application) {

    constructor(
        application: Application,
        repository: PersonalTripRepository
    ) : this(application, repository, AndroidPersonalTripRuntime(application))

    private val _uiState = MutableStateFlow(
        PersonalTripUiState(
            formTarih = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        )
    )
    val uiState: StateFlow<PersonalTripUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        load()
        if (PersonalTripTrackingState.consumeLocationPermissionReminder(application)) {
            noteLocationPermissionRequired()
        }
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
                val personPlateSuggestions = runCatching {
                    repository.getPersonPlateSuggestions()
                }.getOrDefault(emptyList())
                val readyPlateSuggestions = buildReadyPlateSuggestions(
                    trips = trips,
                    personSuggestions = personPlateSuggestions
                )
                _uiState.value = _uiState.value.copy(
                    trips = trips,
                    readyPlates = readyPlateSuggestions.map { it.plaka }.distinct(),
                    readyPlateSuggestions = readyPlateSuggestions,
                    isLoading = false
                )
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

    private fun buildReadyPlateSuggestions(
        trips: List<PersonalTrip>,
        personSuggestions: List<PersonPlateSuggestion>
    ): List<PersonPlateSuggestion> {
        val tripSuggestions = trips.asSequence()
            .mapNotNull { trip ->
                val plate = trip.plaka.trim().uppercase(Locale.ROOT)
                if (plate.isBlank()) return@mapNotNull null
                PersonPlateSuggestion(
                    plaka = plate,
                    plakaUlkesi = PlateCountries.normalize(trip.plakaUlkesi)
                )
            }
            .toList()
        return (personSuggestions + tripSuggestions)
            .filter { it.plaka.isNotBlank() }
            .distinctBy { "${it.plaka}|${it.normalizedCountry}" }
    }

    fun updateFormField(field: String, value: String) {
        _uiState.value = _uiState.value.copy(
            formAracTuru      = if (field == "aracTuru") value else _uiState.value.formAracTuru,
            formPlaka         = if (field == "plaka") value.uppercase() else _uiState.value.formPlaka,
            formPlakaUlkesi   = if (field == "plakaUlkesi") PlateCountries.normalize(value) else _uiState.value.formPlakaUlkesi,
            formHavaDurumu    = if (field == "hava") value else _uiState.value.formHavaDurumu,
            formTarih         = if (field == "tarih") value else _uiState.value.formTarih,
            formYolcuSayisi   = if (field == "yolcuSayisi") value else _uiState.value.formYolcuSayisi,
            formNot           = if (field == "not") value else _uiState.value.formNot,
            formAracMenuOpen  = if (field == "aracTuru") false else if (field == "aracMenu") value == "true" else _uiState.value.formAracMenuOpen,
            formHavaMenuOpen  = if (field == "hava") false else if (field == "havaMenu") value == "true" else _uiState.value.formHavaMenuOpen
        )
    }

    fun selectPlateSuggestion(suggestion: PersonPlateSuggestion) {
        _uiState.value = _uiState.value.copy(
            formPlaka = suggestion.plaka.uppercase(Locale.ROOT),
            formPlakaUlkesi = suggestion.normalizedCountry
        )
    }

    fun resetForm(keepPlate: Boolean = false, keepWeather: Boolean = false) {
        val current = _uiState.value
        _uiState.value = current.copy(
            formAracTuru = "Otomobil",
            formPlaka = if (keepPlate) current.formPlaka else "",
            formPlakaUlkesi = if (keepPlate) current.formPlakaUlkesi else PlateCountries.DEFAULT,
            formHavaDurumu = if (keepWeather) current.formHavaDurumu else "Bilinmiyor",
            formTarih = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            formSurucu = null,
            formYolcuSayisi = "",
            formNot = "",
            formAracMenuOpen = false,
            formHavaMenuOpen = false
        )
    }

    /** İnline formdan Firestore'a taslak kaydeder. */
    fun saveFromInlineForm(startTracking: Boolean = false, context: Context? = null) {
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
                    plakaUlkesi = PlateCountries.normalize(s.formPlakaUlkesi),
                    havaDurumu  = s.formHavaDurumu,
                    surucu      = s.formSurucu,
                    yolcuSayisi = s.formYolcuSayisi.toIntOrNull(),
                    not         = s.formNot
                )
                val docId = repository.saveDraft(trip)
                resetForm(keepPlate = true, keepWeather = true)
                if (startTracking && context != null) {
                    startTripTracking(context, docId)
                } else {
                    _uiState.value = _uiState.value.copy(statusMessage = "✅ Kayıt eklendi")
                }
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Kaydedilemedi: ${e.message}")
            }
        }
    }

    // ── Dialog (yalnızca düzenleme için) ──────────────────────────────────────

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
        plakaUlkesi: String = PlateCountries.DEFAULT,
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
                    plakaUlkesi = PlateCountries.normalize(plakaUlkesi),
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
                    "plakaUlkesi" to PlateCountries.normalize(trip.plakaUlkesi),
                    "havaDurumu"  to trip.havaDurumu,
                    "tarih"       to trip.tarih,
                    "kaldigiSaat" to trip.kaldigiSaat,
                    "varisSaat"   to trip.varisSaat,
                    "surucu"      to trip.surucu,
                    "yolcuSayisi" to trip.yolcuSayisi,
                    "not"         to trip.not
                )
                val updated = repository.updateTrip(trip.firestoreDocId, fields)
                if (!updated) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Güncellenemedi: Firestore işlemi başarısız oldu"
                    )
                    return@launch
                }
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
                val deleted = repository.deleteTrip(docId)
                if (!deleted) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Silinemedi: Firestore işlemi başarısız oldu"
                    )
                    return@launch
                }
                if (_uiState.value.activeDocId == docId) {
                    runtime.stopTracking(getApplication(), docId)
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
            if (startTripTracking(context, docId)) {
                load()
            }
        }
    }

    private suspend fun startTripTracking(context: Context, docId: String): Boolean {
        if (!runtime.hasLocationPermission(context)) {
            noteLocationPermissionRequired()
            return false
        }

        _uiState.value = _uiState.value.copy(
            isResolvingLocation = true,
            statusMessage = "Konum alınıyor..."
        )
        val now = LocalTime.now().format(timeFormatter)
        val location = runtime.resolveCurrentLocation()
        val fields = mutableMapOf<String, Any?>(
            "kaldigiSaat" to now,
            "durum"       to PersonalTrip.DURUM_AKTIF
        )
        if (location != null) {
            fields["kaldigiYer"] = location.first
            fields["kaldigiLat"] = location.second
            fields["kaldigiLng"] = location.third
        }
        val updated = repository.updateTrip(docId, fields)
        if (!updated) {
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = false,
                statusMessage = "Biniş kaydedilemedi: Firestore işlemi başarısız oldu"
            )
            return false
        }
        _uiState.value = _uiState.value.copy(
            isResolvingLocation = false,
            activeDocId = docId,
            statusMessage = if (location != null)
                "Biniş kaydedildi — ${location.first}"
            else
                "Biniş kaydedildi (konum alınamadı)"
        )
        try {
            if (!runtime.hasLocationPermission(context)) {
                PersonalTripPermissionGuard.handleMissingLocationPermission(
                    context = context,
                    source = "manual_start",
                    notifyUser = false
                )
                val rolledBack = repository.updateTrip(
                    docId,
                    mapOf("durum" to PersonalTrip.DURUM_BEKLEMEDE)
                )
                _uiState.value = _uiState.value.copy(
                    activeDocId = null,
                    statusMessage = if (rolledBack) {
                        LOCATION_PERMISSION_REQUIRED_MESSAGE
                    } else {
                        "$LOCATION_PERMISSION_REQUIRED_MESSAGE; kayıt geri alınamadı"
                    }
                )
                return false
            }
            runtime.startTracking(context, docId)
        } catch (e: Exception) {
            val rolledBack = repository.updateTrip(
                docId,
                mapOf("durum" to PersonalTrip.DURUM_BEKLEMEDE)
            )
            _uiState.value = _uiState.value.copy(
                activeDocId = null,
                statusMessage = if (rolledBack) {
                    "Takip başlatılamadı: ${e.message}"
                } else {
                    "Takip başlatılamadı ve kayıt geri alınamadı: ${e.message}"
                }
            )
            return false
        }
        return true
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
            val finalization = runtime.stopTrackingAndAwaitFinalization(context, docId) ?: run {
                _uiState.value = _uiState.value.copy(
                    isResolvingLocation = false,
                    statusMessage = "Son mesafe hesaplaması tamamlanamadı. Lütfen tekrar deneyin."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isResolvingLocation = true,
                statusMessage = "Konum alınıyor..."
            )
            val now = LocalTime.now().format(timeFormatter)
            val location = runtime.resolveCurrentLocation()

            val km = finalization.distanceKm
            val mesafe = if (km > 0.0) String.format(Locale.US, "%.1f km", km) else ""

            // Bindim saatini bul (yolSuresi hesabı için)
            val activeTrip = _uiState.value.trips.find { it.firestoreDocId == docId }
            val yolSuresi = TransitRecordCalculations.computeYolSuresi(
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
            val updated = repository.updateTrip(docId, fields)
            if (!updated) {
                _uiState.value = _uiState.value.copy(
                    isResolvingLocation = false,
                    statusMessage = "İniş kaydedilemedi: Firestore işlemi başarısız oldu"
                )
                return@launch
            }
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

    fun noteLocationPermissionRequired() {
        PersonalTripTrackingState.markLocationPermissionReminder(getApplication())
        _uiState.value = _uiState.value.copy(
            isResolvingLocation = false,
            statusMessage = LOCATION_PERMISSION_REQUIRED_MESSAGE
        )
    }

    fun hasLocationPermission() =
        runtime.hasLocationPermission(getApplication())

    private companion object {
        const val LOCATION_PERMISSION_REQUIRED_MESSAGE =
            "Kişisel seyahat takibi için konum izni gerekli"
    }
}
