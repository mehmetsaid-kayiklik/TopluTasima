package com.example.toplutasima.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveAdvancedRepository
import com.example.toplutasima.drive.repository.DriveStorageFailureCategory
import com.example.toplutasima.drive.repository.DriveTripRepository
import com.example.toplutasima.drive.repository.DriveVehicleRepository
import com.example.toplutasima.drive.validation.DriveDecimalParseResult
import com.example.toplutasima.drive.validation.DriveDecimalParser
import com.example.toplutasima.drive.validation.DriveTripValidator
import com.example.toplutasima.drive.validation.DriveValidationCode
import com.example.toplutasima.drive.validation.DriveValidationIssue
import com.example.toplutasima.drive.validation.DriveVehicleValidator
import com.example.toplutasima.drive.management.DriveVehicleListProcessor
import com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter
import com.example.toplutasima.drive.model.DriveVehicleSort
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.model.VehicleTransmissionType
import com.example.toplutasima.drive.model.VehicleBodyType
import com.example.toplutasima.drive.sync.DriveSyncEntityType
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shared.vehicleassignment.contract.OpaqueDocumentId

class DriveViewModel(
    private val vehicleRepository: DriveVehicleRepository,
    private val tripRepository: DriveTripRepository,
    private val advancedRepository: DriveAdvancedRepository = DriveAdvancedRepository.Disabled,
    private val vehicleValidator: DriveVehicleValidator = DriveVehicleValidator(),
    private val tripValidator: DriveTripValidator = DriveTripValidator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.systemDefaultZone(),
    enabled: Boolean = DriveFeatureFlags.DRIVE_CORE
) : ViewModel() {

    private val featureEnabled = enabled
    private val _uiState = MutableStateFlow(
        DriveUiState(
            featureEnabled = enabled,
            vehiclesLoading = enabled
        )
    )
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private var vehicleListJob: Job? = null
    private var vehicleDetailJob: Job? = null
    private var healthJob: Job? = null
    private var receiptJob: Job? = null
    private val mutationInFlight = AtomicBoolean(false)
    private val listCriteria = MutableStateFlow(
        com.example.toplutasima.drive.model.DriveVehicleListCriteria()
    )

    init {
        if (featureEnabled) {
            try {
                vehicleRepository.schedulePendingSync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        notice = DriveUiNotice(
                            DriveNoticeKind.ERROR,
                            DriveUiMessage.SCHEDULING_FAILURE
                        )
                    )
                }
            }
            observeVehicleList()
            observeAdvancedState()
        }
    }

    fun updateSearchQuery(query: String) {
        listCriteria.update { it.copy(query = query) }
    }

    fun updateFuelFilter(fuelType: VehicleFuelType?) {
        listCriteria.update { it.copy(fuelType = fuelType) }
    }

    fun updateAssignmentFilter(filter: DriveVehicleAssignmentFilter) {
        listCriteria.update { it.copy(assignment = filter) }
    }

    fun updateSort(sort: DriveVehicleSort) {
        listCriteria.update { it.copy(sort = sort) }
    }

    fun toggleSortDirection() {
        listCriteria.update { it.copy(descending = !it.descending) }
    }

    fun toggleSelectionMode() {
        _uiState.update { state ->
            val enabled = !state.selectionMode
            state.copy(
                selectionMode = enabled,
                selectedVehicleIds = if (enabled) state.selectedVehicleIds else emptySet()
            )
        }
    }

    fun toggleVehicleSelection(vehicleId: String) {
        if (!_uiState.value.selectionMode || vehicleId.isBlank()) return
        _uiState.update { state ->
            val selected = state.selectedVehicleIds.toMutableSet()
            if (!selected.add(vehicleId)) selected.remove(vehicleId)
            state.copy(selectedVehicleIds = selected)
        }
    }

    fun requestBulkDelete() {
        val selected = _uiState.value.selectedVehicleIds
        if (selected.isEmpty()) return
        _uiState.update { it.copy(deletePrompt = DriveDeletePrompt.BulkVehicles(selected)) }
    }

    fun retryVehicleList() {
        if (!featureEnabled) return
        observeVehicleList()
    }

    fun openVehicle(vehicleId: String) {
        openVehicleInternal(vehicleId, external = false)
    }

    fun openExternalVehicle(vehicleId: String) {
        if (!OpaqueDocumentId.isValid(vehicleId)) return
        try {
            vehicleRepository.schedulePendingSync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Repository observation remains the authorization boundary even if scheduling fails.
        }
        openVehicleInternal(vehicleId, external = true)
    }

    private fun openVehicleInternal(vehicleId: String, external: Boolean) {
        if (!featureEnabled || vehicleId.isBlank()) return
        _uiState.update {
            it.copy(
                destination = DriveDestination.VehicleDetail(vehicleId),
                selectedVehicle = null,
                selectedVehicleSummary = null,
                selectedVehicleTrips = emptyList(),
                selectedVehicleProvenance = emptyList(),
                detailLoading = true,
                detailError = null,
                externalVehicleRequest = external,
                vehicleForm = null,
                tripForm = null,
                deletePrompt = null
            )
        }
        observeVehicleDetail(vehicleId)
    }

    fun showVehicleList() {
        if (!featureEnabled) return
        vehicleDetailJob?.cancel()
        vehicleDetailJob = null
        _uiState.update {
            it.copy(
                destination = DriveDestination.VehicleList,
                selectedVehicle = null,
                selectedVehicleSummary = null,
                selectedVehicleTrips = emptyList(),
                selectedVehicleProvenance = emptyList(),
                detailLoading = false,
                detailError = null,
                externalVehicleRequest = false,
                vehicleForm = null,
                tripForm = null,
                deletePrompt = null
            )
        }
    }

    fun goBack() {
        when (val destination = _uiState.value.destination) {
            DriveDestination.VehicleList -> Unit
            is DriveDestination.VehicleDetail -> showVehicleList()
            is DriveDestination.VehicleEditor -> {
                destination.vehicleId?.let(::openVehicle) ?: showVehicleList()
            }
            is DriveDestination.TripEditor -> openVehicle(destination.vehicleId)
        }
    }

    fun startAddVehicle() {
        if (!featureEnabled) return
        vehicleDetailJob?.cancel()
        vehicleDetailJob = null
        _uiState.update {
            it.copy(
                destination = DriveDestination.VehicleEditor(vehicleId = null),
                vehicleForm = DriveVehicleFormState(),
                tripForm = null,
                deletePrompt = null,
                notice = null
            )
        }
    }

    fun startEditVehicle() {
        if (!featureEnabled) return
        val vehicle = _uiState.value.selectedVehicle ?: return
        vehicleDetailJob?.cancel()
        vehicleDetailJob = null
        _uiState.update {
            it.copy(
                destination = DriveDestination.VehicleEditor(vehicle.id),
                vehicleForm = vehicle.toFormState(),
                tripForm = null,
                deletePrompt = null,
                notice = null
            )
        }
    }

    fun updateVehicleForm(form: DriveVehicleFormState) {
        val current = _uiState.value.vehicleForm ?: return
        _uiState.update {
            it.copy(
                vehicleForm = form.copy(
                    editingVehicleId = current.editingVehicleId,
                    assignedPersonId = current.assignedPersonId,
                    fieldErrors = emptyMap()
                ),
                notice = null
            )
        }
    }

    fun clearVehiclePersonAssignment() {
        val current = _uiState.value.vehicleForm ?: return
        _uiState.update {
            it.copy(
                vehicleForm = current.copy(
                    assignedPersonId = null,
                    fieldErrors = current.fieldErrors - FIELD_ASSIGNED_PERSON_ID
                )
            )
        }
    }

    fun saveVehicle() {
        if (!featureEnabled || _uiState.value.isMutating) return
        val form = _uiState.value.vehicleForm ?: return
        val draft = buildVehicleDraft(form) ?: return
        runMutation(
            block = {
                form.editingVehicleId?.let { vehicleId ->
                    vehicleRepository.updateVehicle(vehicleId, draft)
                } ?: vehicleRepository.createVehicle(draft)
            },
            onResult = ::handleVehicleSaveResult
        )
    }

    fun startAddTrip() {
        if (!featureEnabled) return
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        val now = LocalDateTime.now(clock).withSecond(0).withNano(0)
        vehicleDetailJob?.cancel()
        vehicleDetailJob = null
        _uiState.update {
            it.copy(
                destination = DriveDestination.TripEditor(vehicleId, tripId = null),
                vehicleForm = null,
                tripForm = DriveTripFormState(
                    vehicleId = vehicleId,
                    startedDate = now.toLocalDate().format(DATE_FORMATTER),
                    startedTime = now.toLocalTime().format(TIME_FORMATTER)
                ),
                deletePrompt = null,
                notice = null
            )
        }
    }

    fun startEditTrip(trip: DriveTrip) {
        if (!featureEnabled) return
        vehicleDetailJob?.cancel()
        vehicleDetailJob = null
        _uiState.update {
            it.copy(
                destination = DriveDestination.TripEditor(trip.vehicleId, trip.id),
                vehicleForm = null,
                tripForm = trip.toFormState(clock.zone),
                deletePrompt = null,
                notice = null
            )
        }
    }

    fun updateTripForm(form: DriveTripFormState) {
        val current = _uiState.value.tripForm ?: return
        _uiState.update {
            it.copy(
                tripForm = form.copy(
                    editingTripId = current.editingTripId,
                    originalStartedAt = current.originalStartedAt,
                    originalEndedAt = current.originalEndedAt,
                    fieldErrors = emptyMap()
                ),
                notice = null
            )
        }
    }

    fun saveTrip() {
        if (!featureEnabled || _uiState.value.isMutating) return
        val form = _uiState.value.tripForm ?: return
        val draft = buildTripDraft(form) ?: return
        runMutation(
            block = {
                form.editingTripId?.let { tripId ->
                    tripRepository.updateTrip(tripId, draft)
                } ?: tripRepository.createTrip(draft)
            },
            onResult = ::handleTripSaveResult
        )
    }

    fun requestVehicleDelete() {
        val vehicle = _uiState.value.selectedVehicle ?: return
        _uiState.update {
            it.copy(
                deletePrompt = DriveDeletePrompt.Vehicle(
                    vehicleId = vehicle.id,
                    activeTripCount = it.selectedVehicleTrips.size
                )
            )
        }
    }

    fun requestTripDelete() {
        val form = _uiState.value.tripForm ?: return
        val tripId = form.editingTripId ?: return
        _uiState.update {
            it.copy(deletePrompt = DriveDeletePrompt.Trip(tripId, form.vehicleId))
        }
    }

    fun dismissDeletePrompt() {
        _uiState.update { it.copy(deletePrompt = null) }
    }

    fun confirmDelete() {
        if (!featureEnabled || _uiState.value.isMutating) return
        when (val prompt = _uiState.value.deletePrompt) {
            null -> Unit
            is DriveDeletePrompt.Vehicle -> confirmVehicleDelete(prompt)
            is DriveDeletePrompt.Trip -> confirmTripDelete(prompt)
            is DriveDeletePrompt.BulkVehicles -> confirmBulkVehicleDelete(prompt)
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun observeVehicleList() {
        vehicleListJob?.cancel()
        vehicleListJob = viewModelScope.launch {
            _uiState.update { it.copy(vehiclesLoading = true, vehiclesError = null) }
            combine(
                vehicleRepository.observeVehicleOverviews(),
                listCriteria
            ) { overviews, criteria ->
                val filtered = withContext(calculationDispatcher) {
                    DriveVehicleListProcessor.apply(overviews, criteria)
                }
                FilteredVehicleSnapshot(criteria, filtered, overviews.size)
            }
                .flowOn(ioDispatcher)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            vehiclesLoading = false,
                            vehiclesError = DriveUiMessage.UNKNOWN_FAILURE
                        )
                    }
                }
                .collectLatest { snapshot ->
                    _uiState.update { state ->
                        val visibleIds = snapshot.vehicles.mapTo(hashSetOf()) { it.vehicle.id }
                        state.copy(
                            vehicles = snapshot.vehicles,
                            totalVehicleCount = snapshot.totalCount,
                            listCriteria = snapshot.criteria,
                            selectedVehicleIds = state.selectedVehicleIds.intersect(visibleIds),
                            vehiclesLoading = false,
                            vehiclesError = null
                        )
                    }
                }
        }
    }

    private fun observeAdvancedState() {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            advancedRepository.observeHealth()
                .flowOn(ioDispatcher)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(emptyList())
                }
                .collectLatest { issues ->
                    _uiState.update { it.copy(healthIssues = issues) }
                }
        }
        receiptJob?.cancel()
        receiptJob = viewModelScope.launch {
            advancedRepository.observeSyncReceipts()
                .flowOn(ioDispatcher)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(emptyList())
                }
                .collectLatest { receipts ->
                    _uiState.update { it.copy(syncReceipts = receipts) }
                }
        }
    }

    private fun observeVehicleDetail(vehicleId: String) {
        vehicleDetailJob?.cancel()
        vehicleDetailJob = viewModelScope.launch {
            combine(
                vehicleRepository.observeVehicle(vehicleId),
                vehicleRepository.observeSummary(vehicleId),
                tripRepository.observeTrips(vehicleId),
                advancedRepository.observeProvenance(
                    DriveSyncEntityType.VEHICLE.name,
                    vehicleId
                )
            ) { vehicle, summary, trips, provenance ->
                DriveDetailSnapshot(vehicle, summary, trips, provenance)
            }
                .flowOn(ioDispatcher)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            detailLoading = false,
                            detailError = DriveUiMessage.UNKNOWN_FAILURE
                        )
                    }
                }
                .collectLatest { snapshot ->
                    _uiState.update {
                        it.copy(
                            selectedVehicle = snapshot.vehicle,
                            selectedVehicleSummary = snapshot.summary,
                            selectedVehicleTrips = snapshot.trips,
                            selectedVehicleProvenance = snapshot.provenance,
                            detailLoading = false,
                            detailError = if (snapshot.vehicle == null) {
                                DriveUiMessage.RECORD_NOT_FOUND
                            } else {
                                null
                            }
                        )
                    }
                }
        }
    }

    private fun buildVehicleDraft(form: DriveVehicleFormState): DriveVehicleDraft? {
        val errors = linkedMapOf<String, DriveFormError>()
        val modelYear = when {
            form.modelYear.isBlank() -> null
            else -> form.modelYear.trim().toIntOrNull().also {
                if (it == null) errors[FIELD_MODEL_YEAR] = DriveFormError.MODEL_YEAR_OUT_OF_RANGE
            }
        }
        val initialOdometer = parseOptionalDecimal(
            form.initialOdometerKm,
            FIELD_INITIAL_ODOMETER,
            errors
        )
        val currentOdometer = parseOptionalDecimal(
            form.currentOdometerKm,
            FIELD_CURRENT_ODOMETER,
            errors
        )
        val engineDisplacement = parseOptionalInt(
            form.engineDisplacementCc,
            FIELD_ENGINE_DISPLACEMENT,
            errors
        )
        val enginePower = parseOptionalInt(form.enginePowerKw, FIELD_ENGINE_POWER, errors)
        val purchasePriceMinor = parseOptionalMoneyMinor(
            form.purchasePrice,
            FIELD_PURCHASE_PRICE,
            errors
        )
        val purchaseDate = parseOptionalDate(form.purchaseDate, FIELD_PURCHASE_DATE, errors)
        val registrationDate = parseOptionalDate(
            form.registrationDate,
            FIELD_REGISTRATION_DATE,
            errors
        )
        val inspectionDueDate = parseOptionalDate(
            form.inspectionDueDate,
            FIELD_INSPECTION_DUE_DATE,
            errors
        )
        val insuranceDueDate = parseOptionalDate(
            form.insuranceDueDate,
            FIELD_INSURANCE_DUE_DATE,
            errors
        )
        val draft = DriveVehicleDraft(
            displayName = form.displayName.trim(),
            brand = form.brand.trimToNull(),
            model = form.model.trimToNull(),
            licensePlate = form.licensePlate.trimToNull(),
            modelYear = modelYear,
            fuelType = form.fuelType,
            initialOdometerKm = initialOdometer,
            currentOdometerKm = currentOdometer,
            assignedPersonId = form.assignedPersonId,
            notes = form.notes.trimToNull(),
            countryCode = form.countryCode.trimToNull(),
            transmissionType = form.transmissionType,
            bodyType = form.bodyType,
            color = form.color.trimToNull(),
            vin = form.vin.trimToNull(),
            engineDisplacementCc = engineDisplacement,
            enginePowerKw = enginePower,
            purchaseDate = purchaseDate,
            purchasePriceMinor = purchasePriceMinor,
            currencyCode = form.currencyCode.trimToNull(),
            trimLevel = form.trimLevel.trimToNull(),
            engineCode = form.engineCode.trimToNull(),
            registrationDate = registrationDate,
            inspectionDueDate = inspectionDueDate,
            insuranceDueDate = insuranceDueDate,
            tireSize = form.tireSize.trimToNull()
        )
        vehicleValidator.validate(draft).forEach { issue ->
            issue.toFormError()?.let { errors.putIfAbsent(issue.field, it) }
        }
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(vehicleForm = form.copy(fieldErrors = errors), notice = null)
            }
            return null
        }
        return draft
    }

    private fun buildTripDraft(form: DriveTripFormState): DriveTripDraft? {
        val errors = linkedMapOf<String, DriveFormError>()
        val startedAt = parseRequiredInstant(
            date = form.startedDate,
            time = form.startedTime,
            original = form.originalStartedAt,
            dateField = FIELD_STARTED_DATE,
            timeField = FIELD_STARTED_TIME,
            errors = errors
        )
        val endedAt = parseOptionalInstant(
            date = form.endedDate,
            time = form.endedTime,
            original = form.originalEndedAt,
            dateField = FIELD_ENDED_DATE,
            timeField = FIELD_ENDED_TIME,
            errors = errors
        )
        val startOdometer = parseOptionalDecimal(
            form.startOdometerKm,
            FIELD_START_ODOMETER,
            errors
        )
        val endOdometer = parseOptionalDecimal(
            form.endOdometerKm,
            FIELD_END_ODOMETER,
            errors
        )
        val distance = parseOptionalDecimal(form.distanceKm, FIELD_DISTANCE, errors)
        if (startedAt == null) {
            applyTripFormErrors(form, errors)
            return null
        }
        val draft = DriveTripDraft(
            vehicleId = form.vehicleId,
            startedAt = startedAt,
            endedAt = endedAt,
            startOdometerKm = startOdometer,
            endOdometerKm = endOdometer,
            distanceKm = distance,
            purpose = form.purpose,
            startLocationName = form.startLocationName.trimToNull(),
            endLocationName = form.endLocationName.trimToNull(),
            notes = form.notes.trimToNull()
        )
        val currentState = _uiState.value
        val vehicleExists = currentState.vehicles.any { it.vehicle.id == form.vehicleId } ||
            currentState.selectedVehicle?.id == form.vehicleId
        tripValidator.validate(draft, vehicleExistsForOwner = vehicleExists).issues.forEach { issue ->
            issue.toFormError()?.let { error ->
                val field = when (issue.field) {
                    FIELD_ENDED_AT -> FIELD_ENDED_DATE
                    else -> issue.field
                }
                errors.putIfAbsent(field, error)
            }
        }
        if (errors.isNotEmpty()) {
            applyTripFormErrors(form, errors)
            return null
        }
        return draft
    }

    private fun applyTripFormErrors(
        form: DriveTripFormState,
        errors: Map<String, DriveFormError>
    ) {
        _uiState.update {
            it.copy(tripForm = form.copy(fieldErrors = errors), notice = null)
        }
    }

    private fun parseOptionalInt(
        raw: String,
        field: String,
        errors: MutableMap<String, DriveFormError>
    ): Int? {
        if (raw.isBlank()) return null
        return raw.trim().toIntOrNull().also { if (it == null) errors[field] = DriveFormError.INVALID_NUMBER }
    }

    private fun parseOptionalMoneyMinor(
        raw: String,
        field: String,
        errors: MutableMap<String, DriveFormError>
    ): Long? {
        if (raw.isBlank()) return null
        return try {
            BigDecimal(raw.trim().replace(',', '.')).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            errors[field] = DriveFormError.INVALID_NUMBER
            null
        } catch (_: NumberFormatException) {
            errors[field] = DriveFormError.INVALID_NUMBER
            null
        }
    }

    private fun parseOptionalDate(
        raw: String,
        field: String,
        errors: MutableMap<String, DriveFormError>
    ): Instant? {
        if (raw.isBlank()) return null
        return try {
            LocalDate.parse(raw.trim(), DATE_FORMATTER).atStartOfDay(clock.zone).toInstant()
        } catch (_: DateTimeParseException) {
            errors[field] = DriveFormError.INVALID_DATE
            null
        }
    }

    private fun handleVehicleSaveResult(result: DriveMutationResult<DriveVehicle>) {
        when (result) {
            is DriveMutationResult.Success -> completeVehicleSave(
                vehicle = result.value,
                notice = DriveUiNotice(DriveNoticeKind.SUCCESS, DriveUiMessage.VEHICLE_SAVED)
            )
            is DriveMutationResult.LocalSavedSyncSchedulingFailed -> completeVehicleSave(
                vehicle = result.value,
                notice = DriveUiNotice(DriveNoticeKind.ERROR, DriveUiMessage.SCHEDULING_FAILURE)
            )
            is DriveMutationResult.ValidationFailed -> applyVehicleRepositoryIssues(result.issues)
            else -> showMutationFailure(result)
        }
    }

    private fun completeVehicleSave(vehicle: DriveVehicle, notice: DriveUiNotice) {
        openVehicle(vehicle.id)
        _uiState.update { it.copy(vehicleForm = null, notice = notice) }
    }

    private fun handleTripSaveResult(result: DriveMutationResult<DriveTrip>) {
        when (result) {
            is DriveMutationResult.Success -> completeTripSave(
                trip = result.value,
                notice = DriveUiNotice(DriveNoticeKind.SUCCESS, DriveUiMessage.TRIP_SAVED)
            )
            is DriveMutationResult.LocalSavedSyncSchedulingFailed -> completeTripSave(
                trip = result.value,
                notice = DriveUiNotice(DriveNoticeKind.ERROR, DriveUiMessage.SCHEDULING_FAILURE)
            )
            is DriveMutationResult.ValidationFailed -> applyTripRepositoryIssues(result.issues)
            else -> showMutationFailure(result)
        }
    }

    private fun completeTripSave(trip: DriveTrip, notice: DriveUiNotice) {
        openVehicle(trip.vehicleId)
        _uiState.update { it.copy(tripForm = null, notice = notice) }
    }

    private fun applyVehicleRepositoryIssues(issues: List<DriveValidationIssue>) {
        val form = _uiState.value.vehicleForm ?: return
        val errors = issues.mapNotNull { issue ->
            issue.toFormError()?.let { issue.field to it }
        }.toMap()
        _uiState.update { it.copy(vehicleForm = form.copy(fieldErrors = errors)) }
    }

    private fun applyTripRepositoryIssues(issues: List<DriveValidationIssue>) {
        val form = _uiState.value.tripForm ?: return
        val errors = issues.mapNotNull { issue ->
            issue.toFormError()?.let { error ->
                val field = if (issue.field == FIELD_ENDED_AT) FIELD_ENDED_DATE else issue.field
                field to error
            }
        }.toMap()
        _uiState.update { it.copy(tripForm = form.copy(fieldErrors = errors)) }
    }

    private fun confirmVehicleDelete(prompt: DriveDeletePrompt.Vehicle) {
        runMutation(
            block = {
                vehicleRepository.deleteVehicle(
                    vehicleId = prompt.vehicleId,
                    deleteLinkedTrips = prompt.activeTripCount > 0
                )
            },
            onResult = { result ->
                when (result) {
                    is DriveMutationResult.Success -> completeVehicleDelete(
                        DriveUiNotice(DriveNoticeKind.SUCCESS, DriveUiMessage.VEHICLE_DELETED)
                    )
                    is DriveMutationResult.LocalSavedSyncSchedulingFailed -> completeVehicleDelete(
                        DriveUiNotice(DriveNoticeKind.ERROR, DriveUiMessage.SCHEDULING_FAILURE)
                    )
                    is DriveMutationResult.CascadeConfirmationRequired -> {
                        _uiState.update {
                            it.copy(
                                deletePrompt = prompt.copy(activeTripCount = result.activeTripCount)
                            )
                        }
                    }
                    else -> showMutationFailure(result)
                }
            }
        )
    }

    private fun completeVehicleDelete(notice: DriveUiNotice) {
        showVehicleList()
        _uiState.update { it.copy(deletePrompt = null, notice = notice) }
    }

    private fun confirmBulkVehicleDelete(prompt: DriveDeletePrompt.BulkVehicles) {
        runMutation(
            block = { advancedRepository.bulkDeleteVehicles(prompt.vehicleIds) },
            onResult = { result ->
                when (result) {
                    is DriveMutationResult.Success -> completeBulkVehicleDelete(
                        DriveUiNotice(
                            DriveNoticeKind.SUCCESS,
                            DriveUiMessage.BULK_VEHICLES_DELETED
                        )
                    )
                    is DriveMutationResult.LocalSavedSyncSchedulingFailed ->
                        completeBulkVehicleDelete(
                            DriveUiNotice(
                                DriveNoticeKind.ERROR,
                                DriveUiMessage.SCHEDULING_FAILURE
                            )
                        )
                    else -> showMutationFailure(result)
                }
            }
        )
    }

    private fun completeBulkVehicleDelete(notice: DriveUiNotice) {
        _uiState.update {
            it.copy(
                selectionMode = false,
                selectedVehicleIds = emptySet(),
                deletePrompt = null,
                notice = notice
            )
        }
    }

    private fun confirmTripDelete(prompt: DriveDeletePrompt.Trip) {
        runMutation(
            block = { tripRepository.deleteTrip(prompt.tripId) },
            onResult = { result ->
                when (result) {
                    is DriveMutationResult.Success -> completeTripDelete(prompt.vehicleId,
                        DriveUiNotice(DriveNoticeKind.SUCCESS, DriveUiMessage.TRIP_DELETED))
                    is DriveMutationResult.LocalSavedSyncSchedulingFailed -> completeTripDelete(
                        prompt.vehicleId,
                        DriveUiNotice(DriveNoticeKind.ERROR, DriveUiMessage.SCHEDULING_FAILURE)
                    )
                    else -> showMutationFailure(result)
                }
            }
        )
    }

    private fun completeTripDelete(vehicleId: String, notice: DriveUiNotice) {
        openVehicle(vehicleId)
        _uiState.update { it.copy(deletePrompt = null, notice = notice) }
    }

    private fun <T> runMutation(
        block: suspend () -> DriveMutationResult<T>,
        onResult: (DriveMutationResult<T>) -> Unit
    ) {
        if (!mutationInFlight.compareAndSet(false, true)) return
        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch(ioDispatcher) {
            try {
                onResult(block())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        notice = DriveUiNotice(
                            DriveNoticeKind.ERROR,
                            DriveUiMessage.UNKNOWN_FAILURE
                        )
                    )
                }
            } finally {
                mutationInFlight.set(false)
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    private fun showMutationFailure(result: DriveMutationResult<*>) {
        val message = when (result) {
            DriveMutationResult.AuthenticationRequired -> DriveUiMessage.AUTHENTICATION_REQUIRED
            DriveMutationResult.OwnershipMismatch -> DriveUiMessage.OWNERSHIP_MISMATCH
            DriveMutationResult.NotFound -> DriveUiMessage.RECORD_NOT_FOUND
            DriveMutationResult.DeletedRecord -> DriveUiMessage.RECORD_DELETED
            is DriveMutationResult.StorageFailure -> when (result.category) {
                DriveStorageFailureCategory.DATABASE -> DriveUiMessage.DATABASE_FAILURE
                DriveStorageFailureCategory.SCHEDULING -> DriveUiMessage.SCHEDULING_FAILURE
                DriveStorageFailureCategory.UNKNOWN -> DriveUiMessage.UNKNOWN_FAILURE
            }
            is DriveMutationResult.CascadeConfirmationRequired,
            is DriveMutationResult.LocalSavedSyncSchedulingFailed<*>,
            is DriveMutationResult.Success<*>,
            is DriveMutationResult.ValidationFailed -> DriveUiMessage.UNKNOWN_FAILURE
        }
        _uiState.update {
            it.copy(notice = DriveUiNotice(DriveNoticeKind.ERROR, message))
        }
    }

    private fun parseOptionalDecimal(
        raw: String,
        field: String,
        errors: MutableMap<String, DriveFormError>
    ): Double? = when (val parsed = DriveDecimalParser.parse(raw)) {
        DriveDecimalParseResult.Empty -> null
        DriveDecimalParseResult.Invalid -> {
            errors[field] = DriveFormError.INVALID_NUMBER
            null
        }
        is DriveDecimalParseResult.Valid -> parsed.value
    }

    private fun parseRequiredInstant(
        date: String,
        time: String,
        original: Instant?,
        dateField: String,
        timeField: String,
        errors: MutableMap<String, DriveFormError>
    ): Instant? {
        if (date.isBlank()) errors[dateField] = DriveFormError.REQUIRED
        if (time.isBlank()) errors[timeField] = DriveFormError.REQUIRED
        if (date.isBlank() || time.isBlank()) return null
        return parseInstant(date, time, original, dateField, timeField, errors)
    }

    private fun parseOptionalInstant(
        date: String,
        time: String,
        original: Instant?,
        dateField: String,
        timeField: String,
        errors: MutableMap<String, DriveFormError>
    ): Instant? {
        if (date.isBlank() && time.isBlank()) return null
        if (date.isBlank()) errors[dateField] = DriveFormError.REQUIRED
        if (time.isBlank()) errors[timeField] = DriveFormError.REQUIRED
        if (date.isBlank() || time.isBlank()) return null
        return parseInstant(date, time, original, dateField, timeField, errors)
    }

    private fun parseInstant(
        date: String,
        time: String,
        original: Instant?,
        dateField: String,
        timeField: String,
        errors: MutableMap<String, DriveFormError>
    ): Instant? {
        if (
            original != null &&
            formatDate(original, clock.zone) == date.trim() &&
            formatTime(original, clock.zone) == time.trim()
        ) {
            return original
        }
        val parsedDate = try {
            LocalDate.parse(date.trim(), DATE_FORMATTER)
        } catch (_: DateTimeParseException) {
            errors[dateField] = DriveFormError.INVALID_DATE
            null
        }
        val parsedTime = try {
            LocalTime.parse(time.trim(), TIME_FORMATTER)
        } catch (_: DateTimeParseException) {
            errors[timeField] = DriveFormError.INVALID_TIME
            null
        }
        if (parsedDate == null || parsedTime == null) return null
        val localDateTime = LocalDateTime.of(parsedDate, parsedTime)
        val validOffsets = clock.zone.rules.getValidOffsets(localDateTime)
        if (validOffsets.isEmpty()) {
            errors[timeField] = DriveFormError.INVALID_TIME
            return null
        }
        return localDateTime.atOffset(validOffsets.first()).toInstant()
    }

    private fun DriveValidationIssue.toFormError(): DriveFormError? = when (code) {
        DriveValidationCode.DISPLAY_NAME_REQUIRED -> DriveFormError.DISPLAY_NAME_REQUIRED
        DriveValidationCode.MODEL_YEAR_OUT_OF_RANGE -> DriveFormError.MODEL_YEAR_OUT_OF_RANGE
        DriveValidationCode.NEGATIVE_ODOMETER -> DriveFormError.NEGATIVE_ODOMETER
        DriveValidationCode.CURRENT_ODOMETER_BEFORE_INITIAL ->
            DriveFormError.CURRENT_ODOMETER_BEFORE_INITIAL
        DriveValidationCode.NEGATIVE_PURCHASE_PRICE,
        DriveValidationCode.INVALID_ENGINE_VALUE -> DriveFormError.INVALID_NUMBER
        DriveValidationCode.INVALID_CURRENCY_CODE,
        DriveValidationCode.INVALID_COUNTRY_CODE -> DriveFormError.INVALID_CODE
        DriveValidationCode.VEHICLE_REQUIRED -> DriveFormError.VEHICLE_REQUIRED
        DriveValidationCode.VEHICLE_NOT_FOUND -> DriveFormError.VEHICLE_NOT_FOUND
        DriveValidationCode.END_BEFORE_START -> DriveFormError.END_BEFORE_START
        DriveValidationCode.NEGATIVE_DISTANCE -> DriveFormError.NEGATIVE_DISTANCE
        DriveValidationCode.END_ODOMETER_BEFORE_START ->
            DriveFormError.END_ODOMETER_BEFORE_START
        DriveValidationCode.DISTANCE_REQUIRED -> DriveFormError.DISTANCE_REQUIRED
        DriveValidationCode.DISTANCE_ODOMETER_MISMATCH ->
            DriveFormError.DISTANCE_ODOMETER_MISMATCH
        DriveValidationCode.INVALID_DECIMAL -> DriveFormError.INVALID_NUMBER
        DriveValidationCode.AUTHENTICATION_REQUIRED,
        DriveValidationCode.OWNERSHIP_MISMATCH,
        DriveValidationCode.RECORD_NOT_FOUND,
        DriveValidationCode.DELETED_RECORD,
        DriveValidationCode.CASCADE_CONFIRMATION_REQUIRED -> null
    }

    private fun DriveVehicle.toFormState(): DriveVehicleFormState = DriveVehicleFormState(
        editingVehicleId = id,
        displayName = displayName,
        brand = brand.orEmpty(),
        model = model.orEmpty(),
        licensePlate = licensePlate.orEmpty(),
        modelYear = modelYear?.toString().orEmpty(),
        fuelType = fuelType,
        initialOdometerKm = initialOdometerKm.toEditableDecimal(),
        currentOdometerKm = currentOdometerKm.toEditableDecimal(),
        assignedPersonId = assignedPersonId,
        notes = notes.orEmpty(),
        countryCode = countryCode.orEmpty(),
        transmissionType = transmissionType,
        bodyType = bodyType,
        color = color.orEmpty(),
        vin = vin.orEmpty(),
        engineDisplacementCc = engineDisplacementCc?.toString().orEmpty(),
        enginePowerKw = enginePowerKw?.toString().orEmpty(),
        purchaseDate = purchaseDate?.let { formatDate(it, clock.zone) }.orEmpty(),
        purchasePrice = purchasePriceMinor.toEditableMoney(),
        currencyCode = currencyCode.orEmpty(),
        trimLevel = trimLevel.orEmpty(),
        engineCode = engineCode.orEmpty(),
        registrationDate = registrationDate?.let { formatDate(it, clock.zone) }.orEmpty(),
        inspectionDueDate = inspectionDueDate?.let { formatDate(it, clock.zone) }.orEmpty(),
        insuranceDueDate = insuranceDueDate?.let { formatDate(it, clock.zone) }.orEmpty(),
        tireSize = tireSize.orEmpty()
    )

    private fun DriveTrip.toFormState(zoneId: ZoneId): DriveTripFormState = DriveTripFormState(
        editingTripId = id,
        vehicleId = vehicleId,
        startedDate = formatDate(startedAt, zoneId),
        startedTime = formatTime(startedAt, zoneId),
        endedDate = endedAt?.let { formatDate(it, zoneId) }.orEmpty(),
        endedTime = endedAt?.let { formatTime(it, zoneId) }.orEmpty(),
        startOdometerKm = startOdometerKm.toEditableDecimal(),
        endOdometerKm = endOdometerKm.toEditableDecimal(),
        distanceKm = distanceKm.toEditableDecimal(),
        purpose = purpose,
        startLocationName = startLocationName.orEmpty(),
        endLocationName = endLocationName.orEmpty(),
        notes = notes.orEmpty(),
        originalStartedAt = startedAt,
        originalEndedAt = endedAt
    )

    private fun Double?.toEditableDecimal(): String = this?.let {
        BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
    }.orEmpty()

    private fun Long?.toEditableMoney(): String = this?.let {
        BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString()
    }.orEmpty()

    private fun String.trimToNull(): String? = trim().takeIf(String::isNotEmpty)

    private data class DriveDetailSnapshot(
        val vehicle: DriveVehicle?,
        val summary: com.example.toplutasima.drive.model.DriveVehicleSummary?,
        val trips: List<DriveTrip>,
        val provenance: List<com.example.toplutasima.drive.model.DriveFieldProvenance>
    )

    private data class FilteredVehicleSnapshot(
        val criteria: com.example.toplutasima.drive.model.DriveVehicleListCriteria,
        val vehicles: List<com.example.toplutasima.drive.model.DriveVehicleOverview>,
        val totalCount: Int
    )

    private companion object {
        const val FIELD_MODEL_YEAR = "modelYear"
        const val FIELD_INITIAL_ODOMETER = "initialOdometerKm"
        const val FIELD_CURRENT_ODOMETER = "currentOdometerKm"
        const val FIELD_ASSIGNED_PERSON_ID = "assignedPersonId"
        const val FIELD_ENGINE_DISPLACEMENT = "engineDisplacementCc"
        const val FIELD_ENGINE_POWER = "enginePowerKw"
        const val FIELD_PURCHASE_DATE = "purchaseDate"
        const val FIELD_PURCHASE_PRICE = "purchasePriceMinor"
        const val FIELD_REGISTRATION_DATE = "registrationDate"
        const val FIELD_INSPECTION_DUE_DATE = "inspectionDueDate"
        const val FIELD_INSURANCE_DUE_DATE = "insuranceDueDate"
        const val FIELD_STARTED_DATE = "startedDate"
        const val FIELD_STARTED_TIME = "startedTime"
        const val FIELD_ENDED_DATE = "endedDate"
        const val FIELD_ENDED_TIME = "endedTime"
        const val FIELD_ENDED_AT = "endedAt"
        const val FIELD_START_ODOMETER = "startOdometerKm"
        const val FIELD_END_ODOMETER = "endOdometerKm"
        const val FIELD_DISTANCE = "distanceKm"

        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT)

        fun formatDate(instant: Instant, zoneId: ZoneId): String =
            instant.atZone(zoneId).toLocalDate().format(DATE_FORMATTER)

        fun formatTime(instant: Instant, zoneId: ZoneId): String =
            instant.atZone(zoneId).toLocalTime().format(TIME_FORMATTER)
    }
}
