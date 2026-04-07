package com.example.toplutasima.model

import kotlinx.serialization.Serializable

enum class UsageType { BOARDING, ALIGHTING, BOTH }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class FavoriteStop(
    val id: String,
    val stopId: String,
    val stopName: String,
    val label: String,
    val usageType: UsageType = UsageType.BOTH
)
