package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.TimeVisualTransformation
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun ActualTimesSection(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage,
    onBoardedClick: () -> Unit
) {
            // --- BİNDİM / İNDİM ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Title row with inline segment selector
                    val tripForSelector = state.trip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S.actualTimes(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (tripForSelector != null && tripForSelector.segments.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.prevSegment() },
                                    enabled = state.selectedSegmentIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("←", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                                Text(
                                    "${state.selectedSegmentIndex + 1}/${tripForSelector.segments.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.nextSegment() },
                                    enabled = state.selectedSegmentIndex < tripForSelector.segments.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) { Text("→", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.selectedSegmentIndex < tripForSelector.segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                            }
                        }
                    }
                    if (tripForSelector != null && tripForSelector.segments.size > 1) {
                        val seg = tripForSelector.segments.getOrNull(state.selectedSegmentIndex)
                        if (seg != null) {
                            Text(
                                "${seg.line} → ${seg.toStop}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bindim
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.customBindimTime,
                            onValueChange = { viewModel.updateCustomBindimTime(it) },
                            label = { Text(S.time(lang)) },
                            placeholder = { Text(S.now(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.customBindimTime.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearCustomBindimTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                        Button(
                            enabled = state.segmentIds.isNotEmpty(),
                            onClick = {
                                onBoardedClick()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(S.boarded(lang)) }
                    }
                    if (state.customBindimTime.isNotBlank()) {
                        TextButton(
                            onClick = { viewModel.undoBindim() },
                            enabled = state.segmentIds.isNotEmpty(),
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(S.undoBoarded(lang)) }
                    }

                    // İndim
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.customIndimTime,
                            onValueChange = { viewModel.updateCustomIndimTime(it) },
                            label = { Text(S.time(lang)) },
                            placeholder = { Text(S.now(lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = TimeVisualTransformation(),
                            trailingIcon = {
                                if (state.customIndimTime.isNotBlank()) {
                                    IconButton(onClick = { viewModel.clearCustomIndimTime() }) {
                                        Icon(Icons.Default.Clear, contentDescription = S.clear(lang))
                                    }
                                }
                            }
                        )
                        Button(
                            enabled = state.segmentIds.isNotEmpty(),
                            onClick = { viewModel.recordIndim() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(S.alighted(lang)) }
                    }
                    if (state.customIndimTime.isNotBlank()) {
                        TextButton(
                            onClick = { viewModel.undoIndim() },
                            enabled = state.segmentIds.isNotEmpty(),
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(S.undoAlighted(lang)) }
                    }
                }
            }
}
