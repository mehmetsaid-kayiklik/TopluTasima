package com.example.toplutasima.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveHealthIssue
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S

@Composable
internal fun DriveVehicleDetailScreen(
    state: DriveUiState,
    onBack: () -> Unit,
    onEditVehicle: () -> Unit,
    onAddTrip: () -> Unit,
    onEditTrip: (DriveTrip) -> Unit,
    onDeleteVehicle: () -> Unit,
    assignmentState: VehicleAssignmentUiState,
    onShowPersonPicker: () -> Unit,
    onHidePersonPicker: () -> Unit,
    onAssignPerson: (String) -> Unit,
    onRemovePerson: () -> Unit,
    onAssignmentMessageShown: () -> Unit,
    onRetry: () -> Unit,
    photoSection: @Composable () -> Unit = {},
    ledgerSection: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    Column(modifier = modifier.fillMaxSize().testTag(DriveUiTestTags.VEHICLE_DETAIL)) {
        DriveScreenHeader(
            title = state.selectedVehicle?.displayName ?: S.driveVehicleDetails(language),
            subtitle = S.driveVehicleDetails(language),
            onBack = onBack,
            trailingContent = {
                if (state.selectedVehicle != null) {
                    IconButton(
                        onClick = onEditVehicle,
                        modifier = Modifier.testTag(DriveUiTestTags.EDIT_VEHICLE)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = S.driveEditVehicle(language)
                        )
                    }
                }
            }
        )

        when {
            state.detailLoading -> androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.detailError != null || state.selectedVehicle == null -> DriveLoadError(
                message = if (state.externalVehicleRequest) {
                    if (language == AppLanguage.TR) {
                        "Araç bu hesapta bulunamadı, silinmiş olabilir veya başka hesaba ait olabilir. İki uygulamada aynı Google hesabını kullanın."
                    } else {
                        "Vehicle was not found in this account, may be deleted, or may belong to another account. Use the same Google account in both apps."
                    }
                } else {
                    (state.detailError ?: DriveUiMessage.RECORD_NOT_FOUND).displayText(language)
                },
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize()
            )
            else -> DriveVehicleDetailContent(
                vehicle = state.selectedVehicle,
                summary = state.selectedVehicleSummary,
                trips = state.selectedVehicleTrips,
                provenance = state.selectedVehicleProvenance,
                healthIssues = state.healthIssues.filter { issue ->
                    issue.vehicleId == state.selectedVehicle.id
                },
                assignmentState = assignmentState,
                language = language,
                onAddTrip = onAddTrip,
                onEditTrip = onEditTrip,
                onDeleteVehicle = onDeleteVehicle,
                onShowPersonPicker = onShowPersonPicker,
                onHidePersonPicker = onHidePersonPicker,
                onAssignPerson = onAssignPerson,
                onRemovePerson = onRemovePerson,
                onAssignmentMessageShown = onAssignmentMessageShown,
                photoSection = photoSection,
                ledgerSection = ledgerSection,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DriveVehicleDetailContent(
    vehicle: DriveVehicle,
    summary: DriveVehicleSummary?,
    trips: List<DriveTrip>,
    provenance: List<DriveFieldProvenance>,
    healthIssues: List<DriveHealthIssue>,
    assignmentState: VehicleAssignmentUiState,
    language: AppLanguage,
    onAddTrip: () -> Unit,
    onEditTrip: (DriveTrip) -> Unit,
    onDeleteVehicle: () -> Unit,
    onShowPersonPicker: () -> Unit,
    onHidePersonPicker: () -> Unit,
    onAssignPerson: (String) -> Unit,
    onRemovePerson: () -> Unit,
    onAssignmentMessageShown: () -> Unit,
    photoSection: @Composable () -> Unit,
    ledgerSection: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "drive-vehicle-basic") {
            DriveDetailCard(title = S.driveBasicInfo(language)) {
                DriveDetailLine(S.driveDisplayName(language), vehicle.displayName)
                vehicle.brand?.takeIf(String::isNotBlank)?.let {
                    DriveDetailLine(S.driveBrand(language), it)
                }
                vehicle.model?.takeIf(String::isNotBlank)?.let {
                    DriveDetailLine(S.driveModel(language), it)
                }
                vehicle.modelYear?.let {
                    DriveDetailLine(S.driveModelYear(language), it.toString())
                }
                vehicle.licensePlate?.takeIf(String::isNotBlank)?.let {
                    DriveDetailLine(S.driveLicensePlate(language), it)
                }
                DriveDetailLine(
                    S.driveFuelType(language),
                    driveStringResource(vehicle.fuelType.labelResource(language))
                )
                vehicle.notes?.takeIf(String::isNotBlank)?.let {
                    DriveDetailLine(S.driveNotes(language), it)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DriveSyncIndicator(vehicle.syncState, language)
                }
            }
        }

        if (com.example.toplutasima.drive.DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS) {
            item(key = "drive-vehicle-photos") { photoSection() }
        }

        if (com.example.toplutasima.drive.DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
            item(key = "drive-vehicle-ledger") { ledgerSection() }
        }

        if (com.example.toplutasima.drive.DriveFeatureFlags.DRIVE_EXTENDED_VEHICLE_PROFILE) {
            item(key = "drive-vehicle-general-profile") {
                DriveDetailCard(title = S.driveProfileGeneral(language)) {
                    vehicle.countryCode?.takeIf(String::isNotBlank)?.let {
                        DriveDetailLine(S.driveCountryCode(language), it)
                    }
                    DriveDetailLine(
                        S.driveTransmission(language),
                        vehicle.transmissionType.name.replace('_', ' ')
                    )
                    DriveDetailLine(S.driveBodyType(language), vehicle.bodyType.name.replace('_', ' '))
                    vehicle.color?.takeIf(String::isNotBlank)?.let {
                        DriveDetailLine(S.driveColor(language), it)
                    }
                    vehicle.trimLevel?.takeIf(String::isNotBlank)?.let {
                        DriveDetailLine(S.driveTrimLevel(language), it)
                    }
                }
            }
            item(key = "drive-vehicle-technical-profile") {
                DriveDetailCard(title = S.driveProfileTechnical(language)) {
                    vehicle.vin?.takeIf(String::isNotBlank)?.let { DriveDetailLine(S.driveVin(language), it) }
                    vehicle.engineCode?.takeIf(String::isNotBlank)?.let {
                        DriveDetailLine(S.driveEngineCode(language), it)
                    }
                    vehicle.engineDisplacementCc?.let {
                        DriveDetailLine(S.driveEngineDisplacement(language), "$it cc")
                    }
                    vehicle.enginePowerKw?.let {
                        DriveDetailLine(S.driveEnginePower(language), "$it kW")
                    }
                    vehicle.tireSize?.takeIf(String::isNotBlank)?.let {
                        DriveDetailLine(S.driveTireSize(language), it)
                    }
                    vehicle.registrationDate?.let {
                        DriveDetailLine(S.driveRegistrationDate(language), formatDriveDate(it, language))
                    }
                    vehicle.inspectionDueDate?.let {
                        DriveDetailLine(S.driveInspectionDueDate(language), formatDriveDate(it, language))
                    }
                    vehicle.insuranceDueDate?.let {
                        DriveDetailLine(S.driveInsuranceDueDate(language), formatDriveDate(it, language))
                    }
                }
            }
            if (vehicle.purchaseDate != null || vehicle.purchasePriceMinor != null) {
                item(key = "drive-vehicle-purchase-profile") {
                    DriveDetailCard(title = S.driveProfilePurchase(language)) {
                        vehicle.purchaseDate?.let {
                            DriveDetailLine(S.drivePurchaseDate(language), formatDriveDate(it, language))
                        }
                        vehicle.purchasePriceMinor?.let { minor ->
                            val amount = java.math.BigDecimal.valueOf(minor, 2)
                                .stripTrailingZeros().toPlainString()
                            DriveDetailLine(
                                S.drivePurchasePrice(language),
                                listOfNotNull(amount, vehicle.currencyCode).joinToString(" ")
                            )
                        }
                    }
                }
            }
        }

        if (com.example.toplutasima.drive.DriveFeatureFlags.DRIVE_PERSON_DIRECTORY) {
            item(key = "drive-vehicle-assignment") {
                VehicleAssignmentSection(
                    state = assignmentState,
                    onShowPicker = onShowPersonPicker,
                    onHidePicker = onHidePersonPicker,
                    onAssign = onAssignPerson,
                    onRemove = onRemovePerson,
                    onMessageShown = onAssignmentMessageShown
                )
            }
        }

        item(key = "drive-vehicle-summary") {
            DriveDetailCard(title = S.driveTotalDistance(language)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DriveCompactMetric(
                        label = S.driveTotalDistance(language),
                        value = formatDriveDistance(summary?.totalDistanceKm ?: 0.0, language),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    DriveCompactMetric(
                        label = S.driveTripCount(language),
                        value = S.driveTripCountValue(summary?.tripCount ?: trips.size, language),
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DriveCompactMetric(
                        label = S.driveLastUsed(language),
                        value = formatDriveInstant(summary?.lastUsedAt, language),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    DriveCompactMetric(
                        label = S.driveInitialOdometer(language),
                        value = formatDriveDistance(
                            summary?.initialOdometerKm ?: vehicle.initialOdometerKm,
                            language
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (healthIssues.isNotEmpty() || provenance.isNotEmpty()) {
            item(key = "drive-vehicle-quality") {
                DriveDetailCard(title = S.driveHealth(language)) {
                    if (healthIssues.isNotEmpty()) {
                        Text(
                            text = S.driveHealthIssueCount(healthIssues.size, language),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (provenance.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = S.driveProvenance(language),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        provenance.forEach { field ->
                            DriveDetailLine(field.fieldName, field.source.displayText(language))
                        }
                    }
                }
            }
        }

        item(key = "drive-vehicle-odometer") {
            DriveOdometerCard(vehicle, summary, language)
        }

        item(key = "drive-trip-heading") {
            Text(
                text = S.driveRecentTrips(language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }

        if (trips.isEmpty()) {
            item(key = "drive-trip-empty") {
                Text(
                    text = S.driveNoTrips(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(items = trips, key = DriveTrip::id) { trip ->
                DriveTripCard(
                    trip = trip,
                    language = language,
                    onClick = { onEditTrip(trip) }
                )
            }
        }

        item(key = "drive-detail-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddTrip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DriveUiTestTags.ADD_TRIP)
                ) {
                    Text(S.driveAddTrip(language))
                }
                OutlinedButton(
                    onClick = onDeleteVehicle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DriveUiTestTags.DELETE_VEHICLE)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(S.delete(language))
                }
            }
        }
    }
}

@Composable
private fun DriveOdometerCard(
    vehicle: DriveVehicle,
    summary: DriveVehicleSummary?,
    language: AppLanguage
) {
    val source = summary?.displayedOdometerSource ?: if (vehicle.currentOdometerKm != null) {
        DriveOdometerSource.MANUAL
    } else {
        DriveOdometerSource.UNAVAILABLE
    }
    val displayedOdometer = summary?.displayedCurrentOdometerKm ?: vehicle.currentOdometerKm
    DriveDetailCard(title = S.driveCurrentOdometer(language)) {
        DriveCompactMetric(
            label = source.displayText(language),
            value = formatDriveDistance(displayedOdometer, language),
            modifier = Modifier.fillMaxWidth()
        )
        if (vehicle.currentOdometerKm != null && summary?.estimatedCurrentOdometerKm != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DriveCompactMetric(
                    label = S.driveOdometerManual(language),
                    value = formatDriveDistance(vehicle.currentOdometerKm, language),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                DriveCompactMetric(
                    label = S.driveOdometerEstimated(language),
                    value = formatDriveDistance(summary.estimatedCurrentOdometerKm, language),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (summary?.hasOdometerInconsistency == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = S.driveOdometerInconsistent(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DriveTripCard(
    trip: DriveTrip,
    language: AppLanguage,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DriveUiTestTags.TRIP_CARD),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDriveInstant(trip.startedAt, language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = driveStringResource(trip.purpose.labelResource(language)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDriveDistance(trip.distanceKm, language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                DriveSyncIndicator(trip.syncState, language)
            }
            listOfNotNull(trip.startLocationName, trip.endLocationName)
                .filter(String::isNotBlank)
                .joinToString(" → ")
                .takeIf(String::isNotBlank)
                ?.let { route ->
                    Text(
                        text = route,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
        }
    }
}

@Composable
private fun DriveDetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun DriveDetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
