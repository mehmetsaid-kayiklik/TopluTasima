package com.example.toplutasima.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.assignment.VehicleAssignment
import com.example.toplutasima.drive.assignment.VehicleAssignmentFailure
import com.example.toplutasima.drive.assignment.VehicleAssignmentMutationResult
import com.example.toplutasima.drive.assignment.VehicleAssignmentRepository
import com.example.toplutasima.drive.assignment.VehiclePersonDirectoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleAssignmentUiState(
    val vehicleId: String? = null,
    val assignment: VehicleAssignment? = null,
    val people: List<VehiclePersonDirectoryEntry> = emptyList(),
    val directoryLoading: Boolean = false,
    val directoryError: Boolean = false,
    val pickerVisible: Boolean = false,
    val working: Boolean = false,
    val message: String? = null
) {
    val activePersonId: String? get() = assignment?.activePersonId
    val assignedPerson: VehiclePersonDirectoryEntry?
        get() = people.firstOrNull { it.personId == activePersonId }
}

class VehicleAssignmentViewModel(
    private val repository: VehicleAssignmentRepository,
    enabled: Boolean = DriveFeatureFlags.DRIVE_PERSON_DIRECTORY
) : ViewModel() {
    private val featureEnabled = enabled
    private val selectedVehicleId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(VehicleAssignmentUiState())
    val uiState: StateFlow<VehicleAssignmentUiState> = _uiState.asStateFlow()

    init {
        if (featureEnabled) {
            viewModelScope.launch {
                combine(
                    selectedVehicleId,
                    repository.observeAssignments(),
                    repository.observeSelectablePeople()
                ) { vehicleId, assignments, people ->
                    Triple(vehicleId, assignments.firstOrNull { it.contract.vehicleId == vehicleId }, people)
                }.collect { (vehicleId, assignment, people) ->
                    _uiState.update {
                        it.copy(vehicleId = vehicleId, assignment = assignment, people = people)
                    }
                }
            }
        }
    }

    fun openVehicle(vehicleId: String?) {
        if (!featureEnabled || vehicleId.isNullOrBlank()) {
            selectedVehicleId.value = null
            return
        }
        selectedVehicleId.value = vehicleId
        refreshDirectory()
    }

    fun showPicker() {
        if (!featureEnabled) return
        _uiState.update { it.copy(pickerVisible = true) }
        refreshDirectory()
    }

    fun hidePicker() {
        _uiState.update { it.copy(pickerVisible = false) }
    }

    fun assign(personId: String) {
        val vehicleId = _uiState.value.vehicleId ?: return
        runMutation { repository.assign(vehicleId, personId) }
    }

    fun remove() {
        val vehicleId = _uiState.value.vehicleId ?: return
        runMutation { repository.remove(vehicleId) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshDirectory() {
        if (_uiState.value.directoryLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(directoryLoading = true, directoryError = false) }
            val result = repository.refreshPersonDirectory()
            _uiState.update {
                it.copy(directoryLoading = false, directoryError = result.isFailure)
            }
        }
    }

    private fun runMutation(block: suspend () -> VehicleAssignmentMutationResult) {
        if (_uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, message = null) }
            val message = try {
                when (val result = block()) {
                    is VehicleAssignmentMutationResult.Success -> "Kişi bağlantısı kaydedildi."
                    is VehicleAssignmentMutationResult.LocalSavedSyncSchedulingFailed ->
                        "Bağlantı cihazda kaydedildi; senkronizasyon bekliyor."
                    is VehicleAssignmentMutationResult.Rejected -> result.failure.userMessage()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            _uiState.update { it.copy(working = false, pickerVisible = false, message = message) }
        }
    }

    private fun VehicleAssignmentFailure.userMessage(): String = when (this) {
        VehicleAssignmentFailure.AuthenticationChanged,
        VehicleAssignmentFailure.AccountMismatch ->
            "İki uygulamada aynı Google hesabını kullandığınızdan emin olun."
        VehicleAssignmentFailure.VehicleNotFound -> "Araç bulunamadı veya başka bir hesaba ait."
        VehicleAssignmentFailure.VehicleDeleted -> "Silinmiş araca kişi atanamaz."
        VehicleAssignmentFailure.PersonNotFound,
        VehicleAssignmentFailure.PersonDeleted -> "Kişi silinmiş veya erişilemiyor."
        VehicleAssignmentFailure.PersonNotShared -> "Kişi artık TopluTaşıma ile paylaşılmıyor."
        VehicleAssignmentFailure.InvalidPersonIdentity -> "Kişi kimliği doğrulanamadı."
        VehicleAssignmentFailure.AssignmentConflict -> "Bağlantı başka bir cihazdaki değişiklikle çakıştı."
        VehicleAssignmentFailure.UnsupportedAssignmentSchema -> "Bağlantı daha yeni bir uygulama sürümüne ait."
        VehicleAssignmentFailure.RetryableRemoteFailure -> "Bağlantı cihazda bekliyor; çevrimiçi olunca eşitlenecek."
        VehicleAssignmentFailure.FatalRemoteFailure,
        VehicleAssignmentFailure.LocalStorageFailure -> "Bağlantı değiştirilemedi."
    }
}
