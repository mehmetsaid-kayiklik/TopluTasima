package com.example.toplutasima.model

data class PersonPlateSuggestion(
    val personId: String = "",
    val displayName: String = "",
    val plaka: String,
    val plakaUlkesi: String = PlateCountries.DEFAULT
) {
    val normalizedCountry: String
        get() = PlateCountries.normalize(plakaUlkesi)
}
