package com.example.toplutasima.ui.screens.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.theme.AmberDark
import com.example.toplutasima.ui.theme.AmberLight
import com.example.toplutasima.ui.theme.BorderDark
import com.example.toplutasima.ui.theme.BorderLight
import com.example.toplutasima.ui.theme.SurfaceD1
import com.example.toplutasima.ui.theme.SurfaceD2
import com.example.toplutasima.ui.theme.SurfaceL1
import com.example.toplutasima.ui.theme.SurfaceL2
import com.example.toplutasima.ui.theme.TextHighDark
import com.example.toplutasima.ui.theme.TextHighLight
import com.example.toplutasima.ui.theme.TextLowDark
import com.example.toplutasima.ui.theme.TextLowLight
import com.example.toplutasima.ui.theme.TextMidDark
import com.example.toplutasima.ui.theme.TextMidLight
import com.example.toplutasima.usecase.ExportFormat
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel

@Composable
private fun isDark() = isSystemInDarkTheme()

// ── LEVEL 2: Day List ──
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DayListScreen(
    monthSummary: MonthSummary,
    dayGroups: List<DayGroup>,
    isLoading: Boolean,
    errorMsg: String,
    lang: com.example.toplutasima.ui.AppLanguage,
    onBack: () -> Unit,
    onTripClick: (Map<String, Any>) -> Unit,
    // Filter props
    filterState: RecordFilterState,
    isFilterPanelOpen: Boolean,
    filteredTotalCount: Int,
    unfilteredTotalCount: Int,
    onToggleFilterPanel: () -> Unit,
    onUpdateFilter: (RecordFilterState) -> Unit,
    onClearFilters: () -> Unit,
    // Incomplete records props
    incompleteRecords: List<RecordRowUiModel>,
    isIncompleteExpanded: Boolean,
    onToggleIncomplete: () -> Unit,
    onIncompleteClick: (Map<String, Any>) -> Unit,
    // Export props
    showExportDialog: Boolean = false,
    isExporting: Boolean = false,
    onToggleExportDialog: () -> Unit = {},
    onExport: (ExportFormat) -> Unit = {},
    onRefresh: () -> Unit
) {
    val dark = isDark()
    val borderColor = if (dark) BorderDark else BorderLight
    val textHigh = if (dark) TextHighDark else TextHighLight
    val textMid = if (dark) TextMidDark else TextMidLight
    val textLow = if (dark) TextLowDark else TextLowLight
    val appBarBg = if (dark) SurfaceD1 else SurfaceL1
    val stickyBg = if (dark) SurfaceD2 else SurfaceL2

    Column(modifier = Modifier.fillMaxSize()) {
        // App Bar equivalent
        Row(
            modifier = Modifier.fillMaxWidth().background(appBarBg).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Back", modifier = Modifier.rotate(-90f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${S.monthName(monthSummary.monthName, lang)} ${monthSummary.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Export button
            IconButton(
                onClick = onToggleExportDialog,
                enabled = !isExporting && dayGroups.isNotEmpty()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = S.exportTitle(lang),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Filter toggle button
            FilledTonalButton(
                onClick = onToggleFilterPanel,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isFilterPanelOpen) S.filterHide(lang) else S.filterShow(lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (filterState.activeFilterCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text("${filterState.activeFilterCount}")
                    }
                }
            }
        }
        Divider(color = borderColor, thickness = 0.5.dp)

        // ── Export Format Dialog ──
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = onToggleExportDialog,
                title = { Text(S.exportChooseFormat(lang), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormatButton(Icons.Outlined.List, S.exportCsv(lang)) { onExport(ExportFormat.CSV) }
                        ExportFormatButton(Icons.Outlined.Create, S.exportJson(lang)) { onExport(ExportFormat.JSON) }
                        ExportFormatButton(Icons.Outlined.DateRange, S.exportPdf(lang)) { onExport(ExportFormat.PDF) }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onToggleExportDialog) { Text(S.cancel(lang)) }
                }
            )
        }

        // ── Incomplete Records Banner ──
        if (incompleteRecords.isNotEmpty()) {
            IncompleteRecordsBanner(
                incompleteRecords = incompleteRecords,
                isExpanded = isIncompleteExpanded,
                onToggle = onToggleIncomplete,
                onRecordClick = onIncompleteClick,
                lang = lang
            )
        }

        // ── Filter Panel ──
        AnimatedVisibility(
            visible = isFilterPanelOpen,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            FilterPanel(
                filterState = filterState,
                onUpdateFilter = onUpdateFilter,
                onClearFilters = onClearFilters,
                lang = lang
            )
        }

        // ── Active filter chips + result count ──
        if (filterState.hasActiveFilters) {
            ActiveFilterBar(
                filterState = filterState,
                filteredCount = filteredTotalCount,
                onUpdateFilter = onUpdateFilter,
                onClearAll = onClearFilters,
                lang = lang
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading && dayGroups.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
                errorMsg.isNotBlank() -> {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                dayGroups.isEmpty() -> {
                    // Empty state - different message if filters are active
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (filterState.hasActiveFilters) {
                                Icons.Outlined.Search
                            } else {
                                Icons.Outlined.DirectionsBus
                            },
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = textLow
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (filterState.hasActiveFilters) S.filterNoResults(lang) else "Yolculuk yok",
                            style = MaterialTheme.typography.titleMedium,
                            color = textHigh,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (filterState.hasActiveFilters) {
                                S.filterNoResultsHint(lang)
                            } else {
                                "Bu güne ait kayıt bulunmuyor"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = textMid,
                            textAlign = TextAlign.Center
                        )
                        if (filterState.hasActiveFilters) {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onClearFilters,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(S.filterClearAll(lang))
                            }
                        }
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            dayGroups.forEach { group ->
                                stickyHeader(key = group.date) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(stickyBg)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${group.dayName}, ${group.date}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textHigh
                                    )
                                }
                            }
                            items(group.trips.size, key = { group.trips[it].id }) { i ->
                                val trip = group.trips[i]
                                TripCard(trip = trip, onClick = { onTripClick(trip.originalRecord) })
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ── Incomplete Records Banner ─────────────────────────────────────────────
@Composable
internal fun IncompleteRecordsBanner(
    incompleteRecords: List<RecordRowUiModel>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRecordClick: (Map<String, Any>) -> Unit,
    lang: com.example.toplutasima.ui.AppLanguage
) {
    val dark = isDark()
    val warning = if (dark) AmberDark else AmberLight
    val recordBg = if (dark) SurfaceD1 else SurfaceL1
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = warning.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge with count
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(warning),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${incompleteRecords.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        S.incompleteTitle(lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        S.incompleteDesc(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) S.incompleteHide(lang) else S.incompleteShowAll(lang),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded: show list of incomplete records
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val maxShow = minOf(incompleteRecords.size, 10) // Don't overflow
                    incompleteRecords.take(maxShow).forEach { record ->
                        val missingLabel = when {
                            record.actualDep.isBlank() && record.actualArr.isBlank() -> S.incompleteMissingBoth(lang)
                            record.actualDep.isBlank() -> S.incompleteMissingDep(lang)
                            else -> S.incompleteMissingArr(lang)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecordClick(record.originalRecord) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = recordBg)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${record.typeDisplay} ${record.line} • ${record.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${record.boardingStop} → ${record.alightingStop}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    missingLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = warning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (incompleteRecords.size > maxShow) {
                        Text(
                            "+${incompleteRecords.size - maxShow} ...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
