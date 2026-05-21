package com.example.toplutasima.viewmodel.bulkupdate

import com.example.toplutasima.model.BulkUpdateRow

enum class BulkUpdatePhase {
    IDLE, LOADING, RUNNING, PAUSED, RATE_LIMITED, DONE
}

enum class BulkUpdateMode {
    DISTANCE_STOPS, STOP_NAMES
}

data class BulkUpdateUiState(
    val phase: BulkUpdatePhase = BulkUpdatePhase.IDLE,
    val mode: BulkUpdateMode = BulkUpdateMode.DISTANCE_STOPS,
    val rows: List<BulkUpdateRow> = emptyList(),
    val totalRows: Int = 0,
    val currentIndex: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val skipCount: Int = 0,
    val currentRowInfo: String = "",
    val rateLimitCountdown: Int = 0,
    val rateLimitReason: String = "",
    val errorMessage: String = "",
    val elapsedMs: Long = 0L,
    val avgMsPerRow: Long = 0L
)
