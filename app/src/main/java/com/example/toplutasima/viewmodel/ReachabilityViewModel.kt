package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.location.NearbyStopsManager
import com.example.toplutasima.model.ReachabilityResult
import com.example.toplutasima.repository.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReachabilityUiState(
    val minutes: Int = 30,
    val loading: Boolean = false,
    val result: ReachabilityResult? = null,
    val status: String = "Konum al ve erisilebilirlik hesapla",
    val hasLocationPermission: Boolean = false
)

class ReachabilityViewModel(
    application: Application,
    private val repository: TripRepository,
    private val nearbyStopsManager: NearbyStopsManager
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        ReachabilityUiState(hasLocationPermission = nearbyStopsManager.hasLocationPermission())
    )
    val uiState: StateFlow<ReachabilityUiState> = _uiState.asStateFlow()

    fun refreshPermissionState() {
        _uiState.value = _uiState.value.copy(hasLocationPermission = nearbyStopsManager.hasLocationPermission())
    }

    fun load(minutes: Int = _uiState.value.minutes) {
        viewModelScope.launch {
            refreshPermissionState()
            if (!nearbyStopsManager.hasLocationPermission()) {
                _uiState.value = _uiState.value.copy(
                    minutes = minutes,
                    loading = false,
                    status = "Konum izni gerekli"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(minutes = minutes, loading = true, status = "Hesaplaniyor...")
            try {
                val location = nearbyStopsManager.currentLocation()
                if (location == null) {
                    _uiState.value = _uiState.value.copy(loading = false, status = "Konum alinamadi")
                    return@launch
                }
                val result = repository.fetchReachability(location.first, location.second, minutes)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    result = result,
                    status = when {
                        !result.supported -> result.message.ifBlank { "Reachability servisi desteklenmiyor" }
                        result.points.isEmpty() -> "Erisilebilir nokta bulunamadi"
                        else -> "${result.points.size} nokta bulundu"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, status = e.message ?: "Hesaplama hatasi")
            }
        }
    }
}
