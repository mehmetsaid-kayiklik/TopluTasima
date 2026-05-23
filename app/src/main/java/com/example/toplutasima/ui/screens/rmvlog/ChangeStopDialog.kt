package com.example.toplutasima.ui.screens.rmvlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState

@Composable
internal fun ChangeStopDialog(
    state: RmvLogUiState,
    viewModel: RmvLogViewModel,
    lang: AppLanguage
) {
    // ── Change Stop Dialog ──
    if (state.changeStopSegIdx >= 0) {
        val segIdx = state.changeStopSegIdx
        val trip = state.trip
        val seg = trip?.segments?.getOrNull(segIdx)
        if (seg != null && seg.stopNames.isNotEmpty()) {
            val mode = state.changeStopMode
            AlertDialog(
                onDismissRequest = { viewModel.dismissChangeStopDialog() },
                title = {
                    Text(
                        if (mode == "binis") S.changeBoardingStop(lang)
                        else if (mode == "inis") S.changeAlightingStop(lang)
                        else S.changeStop(lang),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Mode selection if not yet chosen
                        if (mode.isBlank()) {
                            FilledTonalButton(
                                onClick = { viewModel.showChangeStopDialog(segIdx, "binis") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(S.changeBoardingStop(lang)) }
                            Spacer(Modifier.height(4.dp))
                            FilledTonalButton(
                                onClick = { viewModel.showChangeStopDialog(segIdx, "inis") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(S.changeAlightingStop(lang)) }
                        } else {
                            // Current value
                            val currentStop = if (mode == "binis") seg.fromStop else seg.toStop
                            val currentTime = if (mode == "binis") seg.dep else seg.arr
                            Text(
                                "${S.oldValue(lang)}: $currentStop ($currentTime)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(S.selectNewStop(lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))

                            seg.stopNames.forEachIndexed { stopIdx, name ->
                                val time = seg.stopTimes.getOrElse(stopIdx) { "" }
                                val isSelected = state.changeStopSelectedIdx == stopIdx
                                Surface(
                                    onClick = { viewModel.selectChangeStop(stopIdx) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isSelected) 4.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${stopIdx + 1}. $name",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (time.isNotBlank()) {
                                            Text(
                                                time,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.changeStopSelectedIdx >= 0) {
                                val newName = seg.stopNames.getOrElse(state.changeStopSelectedIdx) { "" }
                                val newTime = seg.stopTimes.getOrElse(state.changeStopSelectedIdx) { "" }
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${S.oldValue(lang)}: $currentStop ($currentTime)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed
                                        )
                                        Text(
                                            "${S.newValue(lang)}: $newName" + if (newTime.isNotBlank()) " ($newTime)" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (mode.isNotBlank() && state.changeStopSelectedIdx >= 0) {
                        Button(onClick = { viewModel.confirmChangeStop() }) {
                            Text(S.confirmChange(lang))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissChangeStopDialog() }) {
                        Text(S.cancelChange(lang))
                    }
                }
            )
        }
    }
}