package com.example.toplutasima.drive.ledger

import shared.vehicleledger.contract.VehicleLedgerIds

enum class VehicleLedgerPage(val segment: String) {
    ODOMETER("odometer"),
    EXPENSES("expenses"),
    REMINDERS("reminders")
}

data class VehicleLedgerRoute(val vehicleId: String, val page: VehicleLedgerPage) {
    val path: String get() = "drive/vehicle/$vehicleId/${page.segment}"

    companion object {
        const val ODOMETER_PATTERN = "drive/vehicle/{vehicleId}/odometer"
        const val EXPENSES_PATTERN = "drive/vehicle/{vehicleId}/expenses"
        const val REMINDERS_PATTERN = "drive/vehicle/{vehicleId}/reminders"

        fun parse(path: String?): VehicleLedgerRoute? {
            val parts = path?.trim('/')?.split('/') ?: return null
            if (parts.size != 4 || parts[0] != "drive" || parts[1] != "vehicle" ||
                !VehicleLedgerIds.isSafe(parts[2])
            ) return null
            val page = VehicleLedgerPage.entries.firstOrNull { it.segment == parts[3] } ?: return null
            return VehicleLedgerRoute(parts[2], page)
        }
    }
}
