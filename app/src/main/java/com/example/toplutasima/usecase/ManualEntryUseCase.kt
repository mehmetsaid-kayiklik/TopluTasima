package com.example.toplutasima.usecase

import com.example.toplutasima.viewmodel.rmvlog.ManualEntryState

class ManualEntryUseCase {
    fun updateManualLine(state: ManualEntryState, value: String): ManualEntryState =
        state.copy(line = value)

    fun updateManualVehicle(state: ManualEntryState, value: String): ManualEntryState =
        state.copy(typeTr = value, typeMenuOpen = false)

    fun updateManualDelay(state: ManualEntryState, value: String): ManualEntryState =
        state.copy(actualDep = value.filter { it.isDigit() }.take(4))

    fun updateManualDuration(state: ManualEntryState, value: String): ManualEntryState =
        state.copy(plannedArr = value.filter { it.isDigit() }.take(4))

    fun updateManualField(state: ManualEntryState, field: String, value: String): ManualEntryState =
        when (field) {
            "type" -> updateManualVehicle(state, value)
            "line" -> updateManualLine(state, value)
            "direction" -> state.copy(direction = value)
            "boardingStop" -> state.copy(boardingStop = value)
            "alightingStop" -> state.copy(alightingStop = value)
            "plannedDep" -> state.copy(plannedDep = value.filter { it.isDigit() }.take(4))
            "actualDep" -> state.copy(actualDep = value.filter { it.isDigit() }.take(4))
            "plannedArr" -> state.copy(plannedArr = value.filter { it.isDigit() }.take(4))
            "actualArr" -> state.copy(actualArr = value.filter { it.isDigit() }.take(4))
            "distance" -> state.copy(distance = value)
            "stopCount" -> state.copy(stopCount = value)
            "weather" -> state.copy(weather = value, weatherMenuOpen = false)
            "note" -> state.copy(note = value)
            "profileId" -> state.copy(profileId = value)
            "seatmateNote" -> state.copy(seatmateNote = value)
            else -> state
        }

    fun validateManualForm(state: ManualEntryState): Boolean =
        state.boardingStop.isNotBlank() && state.alightingStop.isNotBlank() && state.line.isNotBlank()

    fun computeManualDuration(plannedDep: String, plannedArr: String): Int =
        TransitTimeUtils.computeDuration(
            TransitTimeUtils.formatTime(plannedDep),
            TransitTimeUtils.formatTime(plannedArr)
        )
}
