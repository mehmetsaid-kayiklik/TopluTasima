package com.example.toplutasima.ui.util

import com.example.toplutasima.model.VehicleType

fun vehicleIcon(typeKey: String): String = when (typeKey) {
    VehicleType.BUS.key -> "🚌"
    VehicleType.SBAHN.key -> "🚆"
    VehicleType.UBAHN.key -> "🚇"
    VehicleType.RERB.key -> "🚂"
    VehicleType.FERNZUG.key -> "🚄"
    VehicleType.STRASSENBAHN.key -> "🚋"
    else -> "🚌"
}
