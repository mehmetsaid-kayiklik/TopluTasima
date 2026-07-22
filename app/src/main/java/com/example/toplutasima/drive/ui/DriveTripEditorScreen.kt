package com.example.toplutasima.drive.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S

@Composable
internal fun DriveTripEditorScreen(
    form: DriveTripFormState?,
    vehicles: List<DriveVehicle>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onFormChange: (DriveTripFormState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    Column(modifier = modifier.fillMaxSize().testTag(DriveUiTestTags.TRIP_EDITOR)) {
        DriveScreenHeader(
            title = if (form?.editingTripId == null) {
                S.driveAddTrip(language)
            } else {
                S.driveEditTrip(language)
            },
            subtitle = S.driveManualTrip(language),
            onBack = onBack
        )
        if (form == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "drive-trip-vehicle") {
                DriveVehicleSelector(
                    vehicles = vehicles,
                    selectedVehicleId = form.vehicleId,
                    error = form.fieldErrors["vehicleId"],
                    onSelected = { onFormChange(form.copy(vehicleId = it)) }
                )
            }
            item(key = "drive-trip-start") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveFormTextField(
                        value = form.startedDate,
                        onValueChange = { onFormChange(form.copy(startedDate = it)) },
                        label = S.driveStartDate(language),
                        error = form.fieldErrors["startedDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language),
                        modifier = Modifier.weight(1f)
                    )
                    DriveFormTextField(
                        value = form.startedTime,
                        onValueChange = { onFormChange(form.copy(startedTime = it)) },
                        label = S.driveStartTime(language),
                        error = form.fieldErrors["startedTime"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveTimeHint(language),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(key = "drive-trip-end") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DriveFormTextField(
                        value = form.endedDate,
                        onValueChange = { onFormChange(form.copy(endedDate = it)) },
                        label = "${S.driveEndDate(language)} (${S.driveOptional(language)})",
                        error = form.fieldErrors["endedDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language),
                        modifier = Modifier.weight(1f)
                    )
                    DriveFormTextField(
                        value = form.endedTime,
                        onValueChange = { onFormChange(form.copy(endedTime = it)) },
                        label = "${S.driveEndTime(language)} (${S.driveOptional(language)})",
                        error = form.fieldErrors["endedTime"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveTimeHint(language),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(key = "drive-trip-start-odometer") {
                DriveFormTextField(
                    value = form.startOdometerKm,
                    onValueChange = { onFormChange(form.copy(startOdometerKm = it)) },
                    label = S.driveStartOdometer(language),
                    error = form.fieldErrors["startOdometerKm"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("km") }
                )
            }
            item(key = "drive-trip-end-odometer") {
                DriveFormTextField(
                    value = form.endOdometerKm,
                    onValueChange = { onFormChange(form.copy(endOdometerKm = it)) },
                    label = S.driveEndOdometer(language),
                    error = form.fieldErrors["endOdometerKm"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("km") }
                )
            }
            form.calculatedDistanceKm?.let { calculatedDistance ->
                item(key = "drive-trip-calculated-distance") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = S.driveCalculatedDistance(
                                formatDriveDistance(calculatedDistance, language),
                                language
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            item(key = "drive-trip-distance") {
                DriveFormTextField(
                    value = form.distanceKm,
                    onValueChange = { onFormChange(form.copy(distanceKm = it)) },
                    label = "${S.driveDistance(language)} (${S.driveOptional(language)})",
                    error = form.fieldErrors["distanceKm"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("km") }
                )
            }
            item(key = "drive-trip-purpose") {
                DrivePurposeSelector(
                    selected = form.purpose,
                    onSelected = { onFormChange(form.copy(purpose = it)) }
                )
            }
            item(key = "drive-trip-start-location") {
                DriveFormTextField(
                    value = form.startLocationName,
                    onValueChange = { onFormChange(form.copy(startLocationName = it)) },
                    label = S.driveStartLocation(language)
                )
            }
            item(key = "drive-trip-end-location") {
                DriveFormTextField(
                    value = form.endLocationName,
                    onValueChange = { onFormChange(form.copy(endLocationName = it)) },
                    label = S.driveEndLocation(language)
                )
            }
            item(key = "drive-trip-notes") {
                DriveFormTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = S.driveNotes(language),
                    minLines = 3
                )
            }
            item(key = "drive-trip-save") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DriveUiTestTags.SAVE_FORM)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(S.save(language))
                        }
                    }
                    if (form.editingTripId != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(S.delete(language))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveVehicleSelector(
    vehicles: List<DriveVehicle>,
    selectedVehicleId: String,
    error: DriveFormError?,
    onSelected: (String) -> Unit
) {
    val language = LocaleManager.currentLanguage
    var expanded by remember { mutableStateOf(false) }
    val selectedName = vehicles.firstOrNull { it.id == selectedVehicleId }?.displayName
        ?: S.driveVehicleNotFound(language)
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${S.driveVehicle(language)}: $selectedName",
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = { Text(vehicle.displayName) },
                        onClick = {
                            expanded = false
                            onSelected(vehicle.id)
                        }
                    )
                }
            }
        }
        error?.let {
            Text(
                text = it.displayText(language),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun DrivePurposeSelector(
    selected: DriveTripPurpose,
    onSelected: (DriveTripPurpose) -> Unit
) {
    val language = LocaleManager.currentLanguage
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${S.drivePurpose(language)}: ${driveStringResource(selected.labelResource(language))}",
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DriveTripPurpose.entries.forEach { purpose ->
                DropdownMenuItem(
                    text = { Text(driveStringResource(purpose.labelResource(language))) },
                    onClick = {
                        expanded = false
                        onSelected(purpose)
                    }
                )
            }
        }
    }
}
