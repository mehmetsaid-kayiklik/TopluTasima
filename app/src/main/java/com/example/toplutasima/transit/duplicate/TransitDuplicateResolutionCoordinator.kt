package com.example.toplutasima.transit.duplicate

import kotlinx.coroutines.CancellationException

sealed interface TransitDuplicateResolutionResult {
    data object Applied : TransitDuplicateResolutionResult
    data class ValidationBlocked(val preview: TransitDuplicateMergePreview) : TransitDuplicateResolutionResult
    data class SaveFailed(val error: Throwable) : TransitDuplicateResolutionResult
    data class DeleteFailed(val error: Throwable) : TransitDuplicateResolutionResult
}

/**
 * Enforces the lossless merge ordering independently from Room/Firestore implementations.
 * Source deletion is unreachable until the merged target write and its metadata callback finish.
 */
class TransitDuplicateResolutionCoordinator(private val enabled: Boolean = true) {
    suspend fun execute(
        preview: TransitDuplicateMergePreview,
        saveMergedRecord: suspend () -> Unit,
        afterMergedRecordSaved: suspend () -> Unit = {},
        deleteSourceRecord: suspend () -> Unit
    ): TransitDuplicateResolutionResult {
        if (!enabled || !preview.canApply) {
            return TransitDuplicateResolutionResult.ValidationBlocked(preview)
        }
        try {
            saveMergedRecord()
            afterMergedRecordSaved()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return TransitDuplicateResolutionResult.SaveFailed(error)
        }
        return try {
            deleteSourceRecord()
            TransitDuplicateResolutionResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            TransitDuplicateResolutionResult.DeleteFailed(error)
        }
    }
}
