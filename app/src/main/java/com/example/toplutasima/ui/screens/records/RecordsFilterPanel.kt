package com.example.toplutasima.ui.screens.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.RecordFilterState

// ── Filter Panel ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterPanel(
    filterState: RecordFilterState,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearFilters: () -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S.filterTitle(lang),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (filterState.hasActiveFilters) {
                    TextButton(
                        onClick = onClearFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(S.filterClearAll(lang), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = { onUpdateFilter(filterState.copy(searchQuery = it)) },
                placeholder = { Text(S.filterSearchHint(lang), style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = {
                    if (filterState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onUpdateFilter(filterState.copy(searchQuery = "")) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Vehicle Type dropdown
            var vehicleTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = vehicleTypeExpanded,
                onExpandedChange = { vehicleTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (filterState.vehicleType.isBlank()) S.filterAll(lang) else filterState.vehicleType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.filterVehicleType(lang), style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleTypeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = vehicleTypeExpanded,
                    onDismissRequest = { vehicleTypeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.filterAll(lang)) },
                        onClick = {
                            onUpdateFilter(filterState.copy(vehicleType = ""))
                            vehicleTypeExpanded = false
                        }
                    )
                    VehicleType.allKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(S.vehicleTypeName(key, lang)) },
                            onClick = {
                                onUpdateFilter(filterState.copy(vehicleType = key))
                                vehicleTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // Weather dropdown
            var weatherExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = weatherExpanded,
                onExpandedChange = { weatherExpanded = it }
            ) {
                val weatherDisplay = if (filterState.weather.isBlank()) S.filterAll(lang)
                    else {
                        S.weatherName(filterState.weather, lang)
                    }
                OutlinedTextField(
                    value = weatherDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(S.filterWeather(lang), style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weatherExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = weatherExpanded,
                    onDismissRequest = { weatherExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(S.filterAll(lang)) },
                        onClick = {
                            onUpdateFilter(filterState.copy(weather = ""))
                            weatherExpanded = false
                        }
                    )
                    S.weatherOptions.forEach { (key, _) ->
                        DropdownMenuItem(
                            text = { Text(S.weatherName(key, lang)) },
                            onClick = {
                                onUpdateFilter(filterState.copy(weather = key))
                                weatherExpanded = false
                            }
                        )
                    }
                }
            }

            // Stop name search
            OutlinedTextField(
                value = filterState.stopName,
                onValueChange = { onUpdateFilter(filterState.copy(stopName = it)) },
                placeholder = { Text(S.filterStopNameHint(lang), style = MaterialTheme.typography.bodySmall) },
                label = { Text(S.filterStopName(lang), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = {
                    if (filterState.stopName.isNotBlank()) {
                        IconButton(onClick = { onUpdateFilter(filterState.copy(stopName = "")) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Delay range
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filterState.minDelay?.toString() ?: "",
                    onValueChange = { onUpdateFilter(filterState.copy(minDelay = it.toIntOrNull())) },
                    label = { Text(S.filterDelayMin(lang), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = filterState.maxDelay?.toString() ?: "",
                    onValueChange = { onUpdateFilter(filterState.copy(maxDelay = it.toIntOrNull())) },
                    label = { Text(S.filterDelayMax(lang), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            // Seated + Ticket toggles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Seated filter chip
                val seatedOptions = listOf<Pair<String, Boolean?>>(
                    S.filterAll(lang) to null,
                    S.filterSeatedYes(lang) to true,
                    S.filterSeatedNo(lang) to false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.filterSeated(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        seatedOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = filterState.seatedFilter == value,
                                onClick = { onUpdateFilter(filterState.copy(seatedFilter = value)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ticketOptions = listOf<Pair<String, Boolean?>>(
                    S.filterAll(lang) to null,
                    S.filterTicketYes(lang) to true,
                    S.filterTicketNo(lang) to false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.filterTicket(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ticketOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = filterState.ticketFilter == value,
                                onClick = { onUpdateFilter(filterState.copy(ticketFilter = value)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Export Format Button ──────────────────────────────────────────────────
@Composable
internal fun ExportFormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Active Filter Bar with chips ──────────────────────────────────────────
@Composable
internal fun ActiveFilterBar(
    filterState: RecordFilterState,
    filteredCount: Int,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearAll: () -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Result count
        Text(
            S.filterResultCount(filteredCount, lang),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filterState.searchQuery.isNotBlank()) {
                ChipItem("\"${filterState.searchQuery}\"") {
                    onUpdateFilter(filterState.copy(searchQuery = ""))
                }
            }
            if (filterState.vehicleType.isNotBlank()) {
                ChipItem(S.vehicleTypeName(filterState.vehicleType, lang)) {
                    onUpdateFilter(filterState.copy(vehicleType = ""))
                }
            }
            if (filterState.weather.isNotBlank()) {
                ChipItem(S.weatherName(filterState.weather, lang)) {
                    onUpdateFilter(filterState.copy(weather = ""))
                }
            }
            if (filterState.seatedFilter != null) {
                val lbl = if (filterState.seatedFilter == true) S.filterSeatedYes(lang) else S.filterSeatedNo(lang)
                ChipItem(lbl) { onUpdateFilter(filterState.copy(seatedFilter = null)) }
            }
            if (filterState.ticketFilter != null) {
                val lbl = if (filterState.ticketFilter == true) S.filterTicketYes(lang) else S.filterTicketNo(lang)
                ChipItem(lbl) { onUpdateFilter(filterState.copy(ticketFilter = null)) }
            }
            if (filterState.minDelay != null || filterState.maxDelay != null) {
                val min = filterState.minDelay?.toString() ?: "0"
                val max = filterState.maxDelay?.toString() ?: "∞"
                ChipItem("$min-$max dk") {
                    onUpdateFilter(filterState.copy(minDelay = null, maxDelay = null))
                }
            }
            if (filterState.stopName.isNotBlank()) {
                ChipItem(filterState.stopName) {
                    onUpdateFilter(filterState.copy(stopName = ""))
                }
            }
            // Clear all
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = S.clear(lang),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChipItem(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        trailingIcon = {
            Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
        },
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp)
    )
}
