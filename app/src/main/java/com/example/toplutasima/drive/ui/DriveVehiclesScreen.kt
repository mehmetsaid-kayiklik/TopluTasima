package com.example.toplutasima.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import org.koin.androidx.compose.koinViewModel

@Composable
fun DriveVehiclesScreen(
    modifier: Modifier = Modifier,
    initialVehicleId: String? = null,
    viewModel: DriveViewModel = koinViewModel(),
    assignmentViewModel: VehicleAssignmentViewModel? = if (DriveFeatureFlags.DRIVE_PERSON_DIRECTORY) {
        koinViewModel()
    } else {
        null
    },
    photoViewModel: VehiclePhotoViewModel? = if (DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS) {
        koinViewModel()
    } else {
        null
    },
    ledgerViewModel: VehicleLedgerViewModel? = if (DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
        koinViewModel()
    } else {
        null
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assignmentState = assignmentViewModel?.uiState?.collectAsStateWithLifecycle()?.value
        ?: VehicleAssignmentUiState()
    val photoState = photoViewModel?.state?.collectAsStateWithLifecycle()?.value
        ?: VehiclePhotoUiState()
    val ledgerState = ledgerViewModel?.state?.collectAsStateWithLifecycle()?.value
        ?: VehicleLedgerUiState()
    LaunchedEffect(initialVehicleId) {
        initialVehicleId?.let(viewModel::openExternalVehicle)
    }
    LaunchedEffect(state.selectedVehicle?.id) {
        assignmentViewModel?.openVehicle(state.selectedVehicle?.id)
        photoViewModel?.openVehicle(state.selectedVehicle?.id)
        ledgerViewModel?.bindVehicle(state.selectedVehicle?.id)
    }
    BackHandler(
        enabled = state.featureEnabled && ledgerState.page == null &&
            state.destination != DriveDestination.VehicleList,
        onBack = viewModel::goBack
    )
    BackHandler(enabled = ledgerState.page != null) { ledgerViewModel?.close() }

    if (!state.featureEnabled) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(DriveUiTestTags.ROOT)
    ) {
        if (ledgerState.page != null) {
            VehicleLedgerPageScreen(
                state = ledgerState,
                onBack = { ledgerViewModel?.close() },
                onSaveOdometer = { entryId, kilometers ->
                    ledgerViewModel?.saveOdometer(entryId, kilometers)
                },
                onDeleteOdometer = { ledgerViewModel?.deleteOdometer(it) },
                onRestoreOdometer = { ledgerViewModel?.restoreOdometer(it) },
                onSaveExpense = { expenseId, amount, currency, exponent, category, kind, vendor, notes ->
                    ledgerViewModel?.saveExpense(
                        expenseId, amount, currency, exponent, category, kind, vendor, notes
                    )
                },
                onDeleteExpense = { ledgerViewModel?.deleteExpense(it) },
                onRestoreExpense = { ledgerViewModel?.restoreExpense(it) },
                onSaveReminder = { reminderId, title, day, odometer, type ->
                    ledgerViewModel?.saveReminder(reminderId, title, day, odometer, type)
                },
                onCompleteReminder = { ledgerViewModel?.completeReminder(it) },
                onSnoozeReminder = { reminderId, day -> ledgerViewModel?.snoozeReminder(reminderId, day) },
                onDisableReminder = { ledgerViewModel?.disableReminder(it) },
                onDeleteReminder = { ledgerViewModel?.deleteReminder(it) },
                onRetry = { ledgerViewModel?.retrySync() }
            )
        } else when (state.destination) {
            DriveDestination.VehicleList -> DriveVehicleListScreen(
                state = state,
                onVehicleClick = viewModel::openVehicle,
                onAddVehicle = viewModel::startAddVehicle,
                onRetry = viewModel::retryVehicleList,
                onSearchChange = viewModel::updateSearchQuery,
                onFuelFilterChange = viewModel::updateFuelFilter,
                onAssignmentFilterChange = viewModel::updateAssignmentFilter,
                onSortChange = viewModel::updateSort,
                onToggleSortDirection = viewModel::toggleSortDirection,
                onToggleSelectionMode = viewModel::toggleSelectionMode,
                onToggleSelection = viewModel::toggleVehicleSelection,
                onBulkDelete = viewModel::requestBulkDelete
            )
            is DriveDestination.VehicleDetail -> DriveVehicleDetailScreen(
                state = state,
                onBack = viewModel::showVehicleList,
                onEditVehicle = viewModel::startEditVehicle,
                onAddTrip = viewModel::startAddTrip,
                onEditTrip = viewModel::startEditTrip,
                onDeleteVehicle = viewModel::requestVehicleDelete,
                assignmentState = assignmentState,
                onShowPersonPicker = { assignmentViewModel?.showPicker() },
                onHidePersonPicker = { assignmentViewModel?.hidePicker() },
                onAssignPerson = { assignmentViewModel?.assign(it) },
                onRemovePerson = { assignmentViewModel?.remove() },
                onAssignmentMessageShown = { assignmentViewModel?.clearMessage() },
                onRetry = {
                    val vehicleId = (state.destination as DriveDestination.VehicleDetail).vehicleId
                    viewModel.openVehicle(vehicleId)
                },
                photoSection = {
                    VehiclePhotoGallerySection(
                        state = photoState,
                        onAdd = { photoViewModel?.add(it) },
                        onDelete = { photoViewModel?.delete(it) },
                        onSetPrimary = { photoViewModel?.setPrimary(it) },
                        onMove = { photoId, direction -> photoViewModel?.move(photoId, direction) },
                        onRetry = { photoViewModel?.retry(it) },
                        onMessageShown = { photoViewModel?.clearMessage() }
                    )
                },
                ledgerSection = {
                    VehicleLedgerDashboardSection(
                        state = ledgerState,
                        onOpen = { ledgerViewModel?.open(it) }
                    )
                }
            )
            is DriveDestination.VehicleEditor -> DriveVehicleEditorScreen(
                form = state.vehicleForm,
                isSaving = state.isMutating,
                onBack = viewModel::goBack,
                onFormChange = viewModel::updateVehicleForm,
                onSave = viewModel::saveVehicle
            )
            is DriveDestination.TripEditor -> DriveTripEditorScreen(
                form = state.tripForm,
                vehicles = state.vehicles.map(DriveVehicleOverview::vehicle),
                isSaving = state.isMutating,
                onBack = viewModel::goBack,
                onFormChange = viewModel::updateTripForm,
                onSave = viewModel::saveTrip,
                onDelete = viewModel::requestTripDelete
            )
        }

        state.notice?.let { notice ->
            DriveNoticeBanner(
                notice = notice,
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    state.deletePrompt?.let { prompt ->
        DriveDeleteConfirmationDialog(
            prompt = prompt,
            isDeleting = state.isMutating,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeletePrompt
        )
    }
}

@Composable
internal fun DriveVehicleListScreen(
    state: DriveUiState,
    onVehicleClick: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onRetry: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFuelFilterChange: (com.example.toplutasima.drive.model.VehicleFuelType?) -> Unit,
    onAssignmentFilterChange: (com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter) -> Unit,
    onSortChange: (com.example.toplutasima.drive.model.DriveVehicleSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onBulkDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    Box(modifier = modifier.fillMaxSize().testTag(DriveUiTestTags.VEHICLE_LIST)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DriveScreenHeader(
                title = S.driveVehiclesTitle(language),
                subtitle = S.driveVehiclesSubtitle(language)
            )
            DriveVehicleListControls(
                state = state,
                language = language,
                onSearchChange = onSearchChange,
                onFuelFilterChange = onFuelFilterChange,
                onAssignmentFilterChange = onAssignmentFilterChange,
                onSortChange = onSortChange,
                onToggleSortDirection = onToggleSortDirection,
                onToggleSelectionMode = onToggleSelectionMode,
                onBulkDelete = onBulkDelete
            )
            when {
                state.vehiclesLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                state.vehiclesError != null -> DriveLoadError(
                    message = state.vehiclesError.displayText(language),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize()
                )
                state.totalVehicleCount == 0 -> DriveEmptyVehicles(
                    language = language,
                    modifier = Modifier.fillMaxSize()
                )
                state.vehicles.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(S.driveNoFilterResults(language))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.vehicles,
                        key = { overview -> overview.vehicle.id }
                    ) { overview ->
                        DriveVehicleCard(
                            overview = overview,
                            language = language,
                            selectionMode = state.selectionMode,
                            selected = overview.vehicle.id in state.selectedVehicleIds,
                            healthIssueCount = state.healthIssues.count { issue ->
                                issue.vehicleId == overview.vehicle.id
                            },
                            onClick = {
                                if (state.selectionMode) onToggleSelection(overview.vehicle.id)
                                else onVehicleClick(overview.vehicle.id)
                            }
                        )
                    }
                }
            }
        }

        if (!state.selectionMode) {
            ExtendedFloatingActionButton(
                onClick = onAddVehicle,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(S.driveAddVehicle(language)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag(DriveUiTestTags.ADD_VEHICLE)
            )
        }
    }
}

@Composable
private fun DriveVehicleCard(
    overview: DriveVehicleOverview,
    language: AppLanguage,
    selectionMode: Boolean,
    selected: Boolean,
    healthIssueCount: Int,
    onClick: () -> Unit
) {
    val vehicle = overview.vehicle
    val summary = overview.summary
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DriveUiTestTags.VEHICLE_CARD),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() }
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    listOfNotNull(vehicle.brand, vehicle.model)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                        .takeIf(String::isNotBlank)
                        ?.let { brandModel ->
                            Text(
                                text = brandModel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                }
                DriveSyncIndicator(vehicle.syncState, language)
            }

            vehicle.licensePlate?.takeIf(String::isNotBlank)?.let { plate ->
                Text(
                    text = plate,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (vehicle.assignedPersonId == null) {
                    S.drivePersonUnassigned(language)
                } else {
                    S.drivePersonUnavailable(language)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (healthIssueCount > 0) {
                Text(
                    text = S.driveHealthIssueCount(healthIssueCount, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DriveCompactMetric(
                    label = S.driveTotalDistance(language),
                    value = formatDriveDistance(summary.totalDistanceKm, language),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                DriveCompactMetric(
                    label = S.driveLastUsed(language),
                    value = formatDriveInstant(summary.lastUsedAt, language),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DriveEmptyVehicles(language: AppLanguage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag(DriveUiTestTags.VEHICLE_EMPTY)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = S.driveNoVehiclesTitle(language),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = S.driveNoVehiclesBody(language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
internal fun DriveScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = S.driveBack(LocaleManager.currentLanguage)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailingContent?.invoke()
    }
}

@Composable
internal fun DriveCompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DriveSyncIndicator(state: DriveSyncState, language: AppLanguage) {
    val (icon, color) = when (state) {
        DriveSyncState.SYNCED -> Icons.Outlined.CloudDone to MaterialTheme.colorScheme.primary
        DriveSyncState.RETRYABLE_ERROR,
        DriveSyncState.PERMANENT_ERROR -> Icons.Outlined.CloudOff to MaterialTheme.colorScheme.error
        DriveSyncState.LOCAL_PENDING,
        DriveSyncState.SYNCING,
        DriveSyncState.UNKNOWN -> Icons.Outlined.CloudSync to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = state.displayText(language),
        tint = color,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
internal fun DriveLoadError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(S.driveRetry(language))
        }
    }
}

@Composable
private fun DriveNoticeBanner(
    notice: DriveUiNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    val containerColor = if (notice.kind == DriveNoticeKind.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (notice.kind == DriveNoticeKind.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = notice.message.displayText(language),
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = S.driveDismiss(language),
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
private fun DriveDeleteConfirmationDialog(
    prompt: DriveDeletePrompt,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocaleManager.currentLanguage
    val isVehicle = prompt is DriveDeletePrompt.Vehicle
    val isBulk = prompt is DriveDeletePrompt.BulkVehicles
    val activeTripCount = (prompt as? DriveDeletePrompt.Vehicle)?.activeTripCount ?: 0
    val bulkCount = (prompt as? DriveDeletePrompt.BulkVehicles)?.vehicleIds?.size ?: 0
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = Modifier.testTag(DriveUiTestTags.DELETE_CONFIRMATION),
        title = {
            Text(
                when {
                    isBulk -> S.driveBulkDeleteTitle(language)
                    isVehicle -> S.driveDeleteVehicleTitle(language)
                    else -> S.driveDeleteTripTitle(language)
                }
            )
        },
        text = {
            Text(
                when {
                    isBulk -> S.driveBulkDeleteBody(bulkCount, language)
                    isVehicle && activeTripCount > 0 ->
                        S.driveDeleteVehicleCascadeBody(activeTripCount, language)
                    isVehicle -> S.driveDeleteVehicleBody(language)
                    else -> S.driveDeleteTripBody(language)
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when {
                            isBulk -> S.driveDeleteSelected(language)
                            isVehicle && activeTripCount > 0 ->
                                S.driveDeleteVehicleAndTrips(language)
                            else -> S.delete(language)
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(S.cancel(language))
            }
        }
    )
}
