package com.example.toplutasima.model

data class PlateCountryOption(
    val code: String,
    val label: String
)

object PlateCountries {
    const val DEFAULT = "TR"

    val options = listOf(
        PlateCountryOption("TR", "TR - Turkiye"),
        PlateCountryOption("DE", "DE - Almanya"),
        PlateCountryOption("NL", "NL - Hollanda"),
        PlateCountryOption("BE", "BE - Belcika"),
        PlateCountryOption("FR", "FR - Fransa")
    )

    fun normalize(code: String?): String {
        val normalized = code.orEmpty().trim().uppercase()
        return options.firstOrNull { it.code == normalized }?.code ?: DEFAULT
    }
}
