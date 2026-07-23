package com.example.toplutasima.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.model.VehicleTransmissionType
import com.example.toplutasima.drive.model.VehicleBodyType
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S

@Composable
internal fun DriveVehicleEditorScreen(
    form: DriveVehicleFormState?,
    isSaving: Boolean,
    onBack: () -> Unit,
    onFormChange: (DriveVehicleFormState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    Column(modifier = modifier.fillMaxSize().testTag(DriveUiTestTags.VEHICLE_EDITOR)) {
        DriveScreenHeader(
            title = if (form?.editingVehicleId == null) {
                S.driveNewVehicle(language)
            } else {
                S.driveEditVehicle(language)
            },
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
            item(key = "drive-vehicle-name") {
                DriveFormTextField(
                    value = form.displayName,
                    onValueChange = { onFormChange(form.copy(displayName = it)) },
                    label = S.driveDisplayName(language),
                    error = form.fieldErrors["displayName"],
                    modifier = Modifier.testTag(DriveUiTestTags.VEHICLE_NAME_INPUT)
                )
            }
            item(key = "drive-vehicle-brand") {
                DriveFormTextField(
                    value = form.brand,
                    onValueChange = { onFormChange(form.copy(brand = it)) },
                    label = S.driveBrand(language)
                )
            }
            item(key = "drive-vehicle-model") {
                DriveFormTextField(
                    value = form.model,
                    onValueChange = { onFormChange(form.copy(model = it)) },
                    label = S.driveModel(language)
                )
            }
            item(key = "drive-vehicle-year") {
                DriveFormTextField(
                    value = form.modelYear,
                    onValueChange = { onFormChange(form.copy(modelYear = it)) },
                    label = S.driveModelYear(language),
                    error = form.fieldErrors["modelYear"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            item(key = "drive-vehicle-plate") {
                DriveFormTextField(
                    value = form.licensePlate,
                    onValueChange = { onFormChange(form.copy(licensePlate = it)) },
                    label = S.driveLicensePlate(language),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    )
                )
            }
            item(key = "drive-vehicle-fuel") {
                DriveFuelTypeSelector(
                    selected = form.fuelType,
                    onSelected = { onFormChange(form.copy(fuelType = it)) }
                )
            }
            item(key = "drive-vehicle-initial-odometer") {
                DriveFormTextField(
                    value = form.initialOdometerKm,
                    onValueChange = { onFormChange(form.copy(initialOdometerKm = it)) },
                    label = S.driveInitialOdometer(language),
                    error = form.fieldErrors["initialOdometerKm"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("km") }
                )
            }
            if (!DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
                item(key = "drive-vehicle-current-odometer") {
                    DriveFormTextField(
                        value = form.currentOdometerKm,
                        onValueChange = { onFormChange(form.copy(currentOdometerKm = it)) },
                        label = S.driveManualCurrentOdometer(language),
                        error = form.fieldErrors["currentOdometerKm"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text("km") }
                    )
                }
            } else {
                item(key = "drive-vehicle-current-odometer-ledger-info") {
                    Text(
                        if (language == com.example.toplutasima.ui.AppLanguage.TR) {
                            "Güncel kilometre, araç detayındaki kilometre geçmişinden yönetilir."
                        } else {
                            "Current odometer is managed from the odometer history on vehicle details."
                        }
                    )
                }
            }
            if (DriveFeatureFlags.DRIVE_EXTENDED_VEHICLE_PROFILE) {
                item(key = "drive-profile-general-heading") {
                    Text(S.driveProfileGeneral(language), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
                item(key = "drive-vehicle-country") {
                    DriveFormTextField(
                        value = form.countryCode,
                        onValueChange = { onFormChange(form.copy(countryCode = it)) },
                        label = S.driveCountryCode(language),
                        error = form.fieldErrors["countryCode"],
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                }
                item(key = "drive-vehicle-transmission") {
                    DriveEnumSelector(
                        label = S.driveTransmission(language),
                        selected = form.transmissionType,
                        values = VehicleTransmissionType.entries,
                        onSelected = { onFormChange(form.copy(transmissionType = it)) }
                    )
                }
                item(key = "drive-vehicle-body") {
                    DriveEnumSelector(
                        label = S.driveBodyType(language),
                        selected = form.bodyType,
                        values = VehicleBodyType.entries,
                        onSelected = { onFormChange(form.copy(bodyType = it)) }
                    )
                }
                item(key = "drive-vehicle-color") {
                    DriveFormTextField(form.color, { onFormChange(form.copy(color = it)) }, S.driveColor(language))
                }
                item(key = "drive-vehicle-trim") {
                    DriveFormTextField(form.trimLevel, { onFormChange(form.copy(trimLevel = it)) }, S.driveTrimLevel(language))
                }
                item(key = "drive-profile-technical-heading") {
                    Text(S.driveProfileTechnical(language), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
                item(key = "drive-vehicle-vin") {
                    DriveFormTextField(
                        form.vin,
                        { onFormChange(form.copy(vin = it)) },
                        S.driveVin(language),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                }
                item(key = "drive-vehicle-engine-code") {
                    DriveFormTextField(form.engineCode, { onFormChange(form.copy(engineCode = it)) }, S.driveEngineCode(language))
                }
                item(key = "drive-vehicle-engine-cc") {
                    DriveFormTextField(
                        form.engineDisplacementCc,
                        { onFormChange(form.copy(engineDisplacementCc = it)) },
                        S.driveEngineDisplacement(language),
                        error = form.fieldErrors["engineDisplacementCc"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("cc") }
                    )
                }
                item(key = "drive-vehicle-engine-power") {
                    DriveFormTextField(
                        form.enginePowerKw,
                        { onFormChange(form.copy(enginePowerKw = it)) },
                        S.driveEnginePower(language),
                        error = form.fieldErrors["enginePowerKw"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("kW") }
                    )
                }
                item(key = "drive-vehicle-tire-size") {
                    DriveFormTextField(form.tireSize, { onFormChange(form.copy(tireSize = it)) }, S.driveTireSize(language))
                }
                item(key = "drive-vehicle-registration-date") {
                    DriveFormTextField(
                        form.registrationDate,
                        { onFormChange(form.copy(registrationDate = it)) },
                        S.driveRegistrationDate(language),
                        error = form.fieldErrors["registrationDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language)
                    )
                }
                item(key = "drive-profile-purchase-heading") {
                    Text(S.driveProfilePurchase(language), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
                item(key = "drive-vehicle-purchase-date") {
                    DriveFormTextField(
                        form.purchaseDate,
                        { onFormChange(form.copy(purchaseDate = it)) },
                        S.drivePurchaseDate(language),
                        error = form.fieldErrors["purchaseDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language)
                    )
                }
                item(key = "drive-vehicle-purchase-price") {
                    DriveFormTextField(
                        form.purchasePrice,
                        { onFormChange(form.copy(purchasePrice = it)) },
                        S.drivePurchasePrice(language),
                        error = form.fieldErrors["purchasePriceMinor"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                item(key = "drive-vehicle-currency") {
                    DriveFormTextField(
                        form.currencyCode,
                        { onFormChange(form.copy(currencyCode = it)) },
                        S.driveCurrencyCode(language),
                        error = form.fieldErrors["currencyCode"],
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                }
                item(key = "drive-vehicle-inspection-date") {
                    DriveFormTextField(
                        form.inspectionDueDate,
                        { onFormChange(form.copy(inspectionDueDate = it)) },
                        S.driveInspectionDueDate(language),
                        error = form.fieldErrors["inspectionDueDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language)
                    )
                }
                item(key = "drive-vehicle-insurance-date") {
                    DriveFormTextField(
                        form.insuranceDueDate,
                        { onFormChange(form.copy(insuranceDueDate = it)) },
                        S.driveInsuranceDueDate(language),
                        error = form.fieldErrors["insuranceDueDate"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = S.driveDateHint(language)
                    )
                }
            }
            item(key = "drive-vehicle-notes") {
                DriveFormTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = S.driveNotes(language),
                    minLines = 3
                )
            }
            item(key = "drive-vehicle-save") {
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
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> DriveEnumSelector(
    label: String,
    selected: T,
    values: List<T>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected.name.replace('_', ' ')}", modifier = Modifier.weight(1f))
            androidx.compose.material3.Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.name.replace('_', ' ')) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun DriveFuelTypeSelector(
    selected: VehicleFuelType,
    onSelected: (VehicleFuelType) -> Unit
) {
    val language = LocaleManager.currentLanguage
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${S.driveFuelType(language)}: ${driveStringResource(selected.labelResource(language))}",
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VehicleFuelType.entries.forEach { fuelType ->
                DropdownMenuItem(
                    text = { Text(driveStringResource(fuelType.labelResource(language))) },
                    onClick = {
                        expanded = false
                        onSelected(fuelType)
                    }
                )
            }
        }
    }
}

@Composable
internal fun DriveFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: DriveFormError? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    suffix: @Composable (() -> Unit)? = null,
    placeholder: String? = null
) {
    val language = LocaleManager.currentLanguage
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { fieldError ->
            { Text(fieldError.displayText(language)) }
        },
        keyboardOptions = keyboardOptions,
        minLines = minLines,
        suffix = suffix,
        singleLine = minLines == 1,
        modifier = modifier.fillMaxWidth()
    )
}
