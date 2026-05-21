package com.example.toplutasima.ui.screens.summary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.HeatmapCalendar
import com.example.toplutasima.ui.components.RmvFooter
import com.example.toplutasima.ui.components.SummaryCard
import com.example.toplutasima.ui.components.formatMin
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.usecase.TravelReportCards
import com.example.toplutasima.viewmodel.SummaryUiState
@Composable
internal fun SummaryHeaderSection(
    state: SummaryUiState,
    showPersonal: Boolean,
    lang: AppLanguage,
    onSheetMenuOpenChange: (Boolean) -> Unit,
    onLoadSheet: (String) -> Unit,
    onTogglePersonal: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(3f)) {
            FilledTonalButton(
                onClick = { if (!showPersonal) onSheetMenuOpenChange(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !showPersonal
            ) {
                Text("📅  ${displaySummarySheet(state.selectedSheet, lang)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(
                expanded = state.sheetMenuOpen,
                onDismissRequest = { onSheetMenuOpenChange(false) }
            ) {
                state.sheetNames.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                displaySummarySheet(name, lang),
                                fontWeight = if (name == state.selectedSheet) FontWeight.Bold else FontWeight.Normal,
                                color = if (name == state.selectedSheet) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onLoadSheet(name) }
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = onTogglePersonal,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = if (showPersonal) ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) else ButtonDefaults.filledTonalButtonColors()
        ) {
            Text("🚗", style = MaterialTheme.typography.titleSmall)
        }
    }
}

internal fun LazyListScope.SummaryFooterSection(
    isLoading: Boolean,
    lang: AppLanguage,
    onRefresh: () -> Unit
) {
    item {
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(
                if (isLoading) S.refreshing(lang) else S.refreshData(lang),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }

    item { Spacer(Modifier.height(4.dp)) }
    item { RmvFooter() }
    item { Spacer(Modifier.height(16.dp)) }
}