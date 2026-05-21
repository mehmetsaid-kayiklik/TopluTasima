package com.example.toplutasima.viewmodel.personaltrip

import com.example.toplutasima.model.PersonalTrip

data class PersonalTripUiState(
    val trips: List<PersonalTrip> = emptyList(),
    val isLoading: Boolean = false,
    val selectedYearMonth: String? = null,
    val showAddDialog: Boolean = false,
    val editingTrip: PersonalTrip? = null,
    val activeDocId: String? = null,
    val liveDistanceKm: Double = 0.0,
    val isTrackingActive: Boolean = false,
    val isResolvingLocation: Boolean = false,
    val statusMessage: String = "",
    val formAracTuru: String = "Otomobil",
    val formPlaka: String = "",
    val formHavaDurumu: String = "Bilinmiyor",
    val formTarih: String = "",
    val formSurucu: Boolean? = null,
    val formYolcuSayisi: String = "",
    val formNot: String = "",
    val readyPlates: List<String> = emptyList(),
    val formAracMenuOpen: Boolean = false,
    val formHavaMenuOpen: Boolean = false
)
