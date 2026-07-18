package com.example.toplutasima.viewmodel.rmvlog

import com.example.toplutasima.domain.transit.validation.TransitRecordValidationResult
import com.example.toplutasima.domain.transit.validation.TransitValidationField

enum class PendingTransitSaveAction {
    RMV_RECORD,
    MANUAL_RECORD
}

data class TransitValidationUiState(
    val showSheet: Boolean = false,
    val result: TransitRecordValidationResult? = null,
    val pendingAction: PendingTransitSaveAction? = null,
    val focusField: TransitValidationField? = null
)
