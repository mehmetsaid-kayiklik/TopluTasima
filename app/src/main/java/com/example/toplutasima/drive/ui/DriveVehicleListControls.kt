package com.example.toplutasima.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter
import com.example.toplutasima.drive.model.DriveVehicleSort
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

@Composable
internal fun DriveVehicleListControls(
    state: DriveUiState,
    language: AppLanguage,
    onSearchChange: (String) -> Unit,
    onFuelFilterChange: (VehicleFuelType?) -> Unit,
    onAssignmentFilterChange: (DriveVehicleAssignmentFilter) -> Unit,
    onSortChange: (DriveVehicleSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onBulkDelete: () -> Unit
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.listCriteria.query,
            onValueChange = onSearchChange,
            label = { Text(S.driveSearch(language)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DriveUiTestTags.SEARCH)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { filterExpanded = true },
                    modifier = Modifier.fillMaxWidth().testTag(DriveUiTestTags.FILTER)
                ) {
                    Text(S.driveFilter(language))
                }
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.driveAllFuelTypes(language)) },
                        onClick = {
                            onFuelFilterChange(null)
                            filterExpanded = false
                        }
                    )
                    VehicleFuelType.entries.forEach { fuelType ->
                        DropdownMenuItem(
                            text = { Text(driveStringResource(fuelType.labelResource(language))) },
                            onClick = {
                                onFuelFilterChange(fuelType)
                                filterExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DriveVehicleAssignmentFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.displayText(language)) },
                            onClick = {
                                onAssignmentFilterChange(filter)
                                filterExpanded = false
                            }
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.fillMaxWidth().testTag(DriveUiTestTags.SORT)
                ) {
                    Text(state.listCriteria.sort.displayText(language))
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    DriveVehicleSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.displayText(language)) },
                            onClick = {
                                onSortChange(sort)
                                sortExpanded = false
                            }
                        )
                    }
                }
            }
            TextButton(onClick = onToggleSortDirection) {
                Text(
                    if (state.listCriteria.descending) {
                        S.driveDescending(language)
                    } else {
                        S.driveAscending(language)
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onToggleSelectionMode,
                modifier = Modifier.testTag(DriveUiTestTags.SELECTION)
            ) {
                Text(
                    if (state.selectionMode) S.driveFinishSelection(language)
                    else S.driveSelect(language)
                )
            }
            if (state.selectionMode) {
                Button(
                    onClick = onBulkDelete,
                    enabled = state.selectedVehicleIds.isNotEmpty() && !state.isMutating,
                    modifier = Modifier.testTag(DriveUiTestTags.BULK_DELETE)
                ) {
                    Text(S.driveDeleteSelected(language))
                }
            }
        }
        if (state.selectionMode) {
            Text(S.driveSelectedCount(state.selectedVehicleIds.size, language))
        }
        state.syncReceipts.firstOrNull()?.let { receipt ->
            Text(
                text = "${S.driveLatestSync(language)}: ${receipt.status.displayText(language)}"
            )
        }
    }
}

private fun DriveVehicleAssignmentFilter.displayText(language: AppLanguage): String = when (this) {
    DriveVehicleAssignmentFilter.ALL -> S.driveAllAssignments(language)
    DriveVehicleAssignmentFilter.ASSIGNED -> S.driveAssignedOnly(language)
    DriveVehicleAssignmentFilter.UNASSIGNED -> S.driveUnassignedOnly(language)
}

private fun DriveVehicleSort.displayText(language: AppLanguage): String = when (this) {
    DriveVehicleSort.NAME -> S.driveSortName(language)
    DriveVehicleSort.LICENSE_PLATE -> S.driveSortPlate(language)
    DriveVehicleSort.FUEL_TYPE -> S.driveSortFuel(language)
    DriveVehicleSort.ASSIGNED_PERSON -> S.driveSortAssignment(language)
    DriveVehicleSort.LAST_USED -> S.driveSortLastUsed(language)
    DriveVehicleSort.TOTAL_DISTANCE -> S.driveSortDistance(language)
}
