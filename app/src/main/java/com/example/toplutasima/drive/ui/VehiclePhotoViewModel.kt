package com.example.toplutasima.drive.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.photo.DriveVehiclePhoto
import com.example.toplutasima.drive.photo.VehiclePhotoFailure
import com.example.toplutasima.drive.photo.VehiclePhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VehiclePhotoUiMessage {
    ADDED,
    DELETED,
    PRIMARY_CHANGED,
    REORDERED,
    RETRY_QUEUED,
    SOURCE_UNAVAILABLE,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    ACCOUNT_CHANGED,
    VEHICLE_DELETED,
    FAILED
}

data class VehiclePhotoUiState(
    val vehicleId: String? = null,
    val photos: List<DriveVehiclePhoto> = emptyList(),
    val loading: Boolean = false,
    val working: Boolean = false,
    val message: VehiclePhotoUiMessage? = null
)

class VehiclePhotoViewModel(
    private val repository: VehiclePhotoRepository,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS
) : ViewModel() {
    private val mutableState = MutableStateFlow(VehiclePhotoUiState())
    val state: StateFlow<VehiclePhotoUiState> = mutableState.asStateFlow()
    private var observation: Job? = null

    init {
        if (enabled) viewModelScope.launch {
            try {
                repository.schedulePendingOperations()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: VehiclePhotoFailure.AuthenticationChanged) {
                mutableState.update { it.copy(message = VehiclePhotoUiMessage.ACCOUNT_CHANGED) }
            }
        }
    }

    fun openVehicle(vehicleId: String?) {
        observation?.cancel()
        if (!enabled || vehicleId.isNullOrBlank()) {
            mutableState.value = VehiclePhotoUiState()
            return
        }
        mutableState.value = VehiclePhotoUiState(vehicleId = vehicleId, loading = true)
        observation = viewModelScope.launch {
            repository.observePhotos(vehicleId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    mutableState.update { it.copy(loading = false, message = VehiclePhotoUiMessage.FAILED) }
                }
                .collect { photos ->
                    mutableState.update { it.copy(photos = photos, loading = false) }
                }
        }
        viewModelScope.launch {
            try {
                repository.ensureLocalCopies(vehicleId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Missing files are persisted as health state and rendered by the gallery.
            }
        }
    }

    fun add(source: Uri) = mutate(VehiclePhotoUiMessage.ADDED) { vehicleId ->
        repository.add(vehicleId, source)
    }

    fun delete(photoId: String) = mutate(VehiclePhotoUiMessage.DELETED) { vehicleId ->
        repository.delete(vehicleId, photoId)
    }

    fun setPrimary(photoId: String) = mutate(VehiclePhotoUiMessage.PRIMARY_CHANGED) { vehicleId ->
        repository.setPrimary(vehicleId, photoId)
    }

    fun move(photoId: String, direction: Int) = mutate(VehiclePhotoUiMessage.REORDERED) { vehicleId ->
        repository.move(vehicleId, photoId, direction)
    }

    fun retry(photoId: String) = mutate(VehiclePhotoUiMessage.RETRY_QUEUED) { vehicleId ->
        repository.retry(vehicleId, photoId)
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun mutate(
        success: VehiclePhotoUiMessage,
        block: suspend (String) -> Any
    ) {
        if (!enabled || mutableState.value.working) return
        val vehicleId = mutableState.value.vehicleId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(working = true, message = null) }
            try {
                block(vehicleId)
                mutableState.update { it.copy(message = success) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VehiclePhotoFailure) {
                mutableState.update { it.copy(message = failure.toUiMessage()) }
            } catch (_: Exception) {
                mutableState.update { it.copy(message = VehiclePhotoUiMessage.FAILED) }
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }

    private fun VehiclePhotoFailure.toUiMessage(): VehiclePhotoUiMessage = when (this) {
        is VehiclePhotoFailure.PhotoSourceUnavailable,
        is VehiclePhotoFailure.PhotoDecodeFailed -> VehiclePhotoUiMessage.SOURCE_UNAVAILABLE
        is VehiclePhotoFailure.PhotoTooLarge -> VehiclePhotoUiMessage.TOO_LARGE
        is VehiclePhotoFailure.UnsupportedPhotoType -> VehiclePhotoUiMessage.UNSUPPORTED_TYPE
        is VehiclePhotoFailure.AuthenticationChanged,
        is VehiclePhotoFailure.AccountMismatch -> VehiclePhotoUiMessage.ACCOUNT_CHANGED
        is VehiclePhotoFailure.VehicleDeleted,
        is VehiclePhotoFailure.VehicleNotFound -> VehiclePhotoUiMessage.VEHICLE_DELETED
        else -> VehiclePhotoUiMessage.FAILED
    }
}
