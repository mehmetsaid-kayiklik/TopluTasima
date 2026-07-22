package com.example.toplutasima.drive.ui

import android.net.Uri
import shared.vehicleassignment.contract.OpaqueDocumentId

object VehicleDeepLinkRoute {
    const val SCHEME = "toplutasima"
    const val AUTHORITY = "drive"
    const val VEHICLE_SEGMENT = "vehicle"

    sealed interface ParseResult {
        data class Valid(val vehicleId: String) : ParseResult
        data object Invalid : ParseResult
    }

    fun parse(uri: Uri?): ParseResult {
        if (uri == null || uri.scheme != SCHEME || uri.authority != AUTHORITY ||
            uri.query != null || uri.fragment != null
        ) return ParseResult.Invalid
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != VEHICLE_SEGMENT) return ParseResult.Invalid
        val vehicleId = segments[1]
        return if (OpaqueDocumentId.isValid(vehicleId)) ParseResult.Valid(vehicleId)
        else ParseResult.Invalid
    }
}
