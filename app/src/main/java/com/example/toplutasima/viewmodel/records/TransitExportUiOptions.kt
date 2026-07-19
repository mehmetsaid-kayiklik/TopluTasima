package com.example.toplutasima.viewmodel.records

import com.example.toplutasima.transit.export.TransitExportFormat
import com.example.toplutasima.transit.export.TransitExportScopeType
import com.example.toplutasima.transit.export.TransitExportSection

/** User choices only; the ViewModel resolves them against the active transit data source. */
data class TransitExportUiOptions(
    val format: TransitExportFormat,
    val scopeType: TransitExportScopeType,
    val startDateIso: String? = null,
    val endDateIso: String? = null,
    val sections: Set<TransitExportSection> = setOf(
        TransitExportSection.RECORDS,
        TransitExportSection.SUMMARY,
        TransitExportSection.DATA_HEALTH
    )
)
