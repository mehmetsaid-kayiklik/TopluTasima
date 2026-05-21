package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.TimeVisualTransformation
import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
@Composable
internal fun DateTimeSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
            // --- TARİH & SAAT CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(S.dateTime(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.date,
                            onValueChange = { viewModel.updateDate(it) },
                            label = { Text(S.date(lang)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = state.time,
                            onValueChange = { viewModel.updateTime(it) },
                            label = { Text(S.time(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.time.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        onClick = { viewModel.fetchDepartures() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(S.fetchTimes(lang), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
}

@Composable
internal fun DepartureSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
            if (state.departures.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(S.departures(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.departures.forEach { dep ->
                            val isSelected = state.selectedDeparture == dep
                            val bgColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                            val emoji = vehicleIcon(dep.typeTr)
                            Surface(
                                onClick = { viewModel.selectDeparture(dep) },
                                shape = RoundedCornerShape(10.dp),
                                color = bgColor,
                                tonalElevation = if (isSelected) 4.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${S.vehicleTypeName(dep.typeTr, lang)} • ${dep.line}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            dep.direction,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Show transfer info only for the selected departure (trip is loaded)
                                        if (isSelected && state.trip != null) {
                                            val t = state.trip!!
                                            val transferText = if (t.segments.size <= 1) {
                                                "(${S.transferDirect(lang)})"
                                            } else {
                                                "(${S.transferCount(t.segments.size - 1, lang)})"
                                            }
                                            Text(
                                                transferText,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (dep.cancelled) {
                                            Text(
                                                "İptal",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                dep.displayTime,
                                                fontWeight = FontWeight.Bold,
                                                color = if (dep.hasRealtime) SuccessGreen else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (!dep.cancelled && dep.hasRealtime) {
                                            Text(
                                                if (dep.isDelayed) "Plan ${dep.time}" else "Canlı",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (dep.track.isNotBlank()) {
                                            Text(
                                                "Gl. ${dep.track}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
}