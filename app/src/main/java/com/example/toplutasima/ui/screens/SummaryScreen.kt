package com.example.toplutasima.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.summary.TransitSummaryDataQuality
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.screens.summary.ComparisonInsightSection
import com.example.toplutasima.ui.screens.summary.DurationDelaySection
import com.example.toplutasima.ui.screens.summary.InsightSection
import com.example.toplutasima.ui.screens.summary.LineDetailSheet
import com.example.toplutasima.ui.screens.summary.SummaryFooterSection
import com.example.toplutasima.ui.screens.summary.SummaryHeaderSection
import com.example.toplutasima.ui.screens.summary.TripStatsSection
import com.example.toplutasima.ui.screens.summary.displaySummarySheet
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.viewmodel.SummaryViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = koinViewModel(),
    showPersonal: Boolean = false,
    onTogglePersonal: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lang = LocaleManager.currentLanguage
    var heatmapMetric by remember(state.selectedSheet) { mutableStateOf(HeatmapMetric.TRIPS) }

    Column(modifier = modifier.fillMaxSize()) {
        SummaryHeaderSection(
            state = state,
            showPersonal = showPersonal,
            lang = lang,
            onSheetMenuOpenChange = viewModel::setSheetMenuOpen,
            onLoadSheet = viewModel::loadData,
            onTogglePersonal = { onTogglePersonal(!showPersonal) }
        )

        if (showPersonal) {
            PersonalSummaryContent(lang = lang)
        } else {
            val tabCount = if (state.selectedSheet != "T\u00fcm\u00fc") 3 else 2
            TabRow(
                selectedTabIndex = state.selectedInnerTab.coerceAtMost(tabCount - 1),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = state.selectedInnerTab == 0,
                    onClick = { viewModel.setSelectedInnerTab(0) },
                    text = { Text(S.tabTripsRecords(lang), fontWeight = if (state.selectedInnerTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = state.selectedInnerTab == 1,
                    onClick = { viewModel.setSelectedInnerTab(1) },
                    text = { Text(S.tabDurationDelay(lang), fontWeight = if (state.selectedInnerTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                if (state.selectedSheet != "T\u00fcm\u00fc") {
                    Tab(
                        selected = state.selectedInnerTab == 2,
                        onClick = { viewModel.setSelectedInnerTab(2) },
                        text = { Text(S.tabComparison(lang), fontWeight = if (state.selectedInnerTab == 2) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading && state.summary == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                } else if (state.errorMsg.isNotBlank() && state.summary == null) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            state.errorMsg,
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (state.summary != null) {
                    val summary = state.summary!!
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = { viewModel.refreshData() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val quality = state.dataQuality
                            if (
                                TransitFeatureFlags.POST_SAVE_DATA_HEALTH &&
                                quality?.assessmentApplied == true &&
                                quality.hasIssues
                            ) {
                                item(key = "transit-summary-data-quality") {
                                    TransitSummaryDataQualityCard(quality = quality, lang = lang)
                                }
                            }
                            when (state.selectedInnerTab) {
                                0 -> {
                                    TripStatsSection(
                                        state = state,
                                        s = summary,
                                        heatmapMetric = heatmapMetric,
                                        onHeatmapMetricChange = { heatmapMetric = it },
                                        displaySheet = { displaySummarySheet(it, lang) },
                                        lang = lang
                                    )
                                    InsightSection(summary, lang)
                                }
                                1 -> DurationDelaySection(
                                    s = summary,
                                    lang = lang,
                                    onLineClick = viewModel::showLineDetail
                                )
                                2 -> ComparisonInsightSection(state, lang)
                            }
                            SummaryFooterSection(
                                isLoading = state.isLoading,
                                lang = lang,
                                onRefresh = { viewModel.refreshData() }
                            )
                        }
                    }
                }
            }
        }
    }

    state.selectedLineDetail?.let { detail ->
        LineDetailSheet(
            detail = detail,
            lang = lang,
            onDismiss = viewModel::dismissLineDetail
        )
    }
}

@Composable
private fun TransitSummaryDataQualityCard(
    quality: TransitSummaryDataQuality,
    lang: AppLanguage
) {
    val details = when (lang) {
        AppLanguage.TR ->
            "${quality.informationalIssueCount} bilgi, ${quality.warningIssueCount} uyarı, " +
                "${quality.criticalIssueCount} kritik sorun. " +
                "${quality.excludedRecordCount} kayıt istatistiklerden çıkarıldı."
        AppLanguage.DE ->
            "${quality.informationalIssueCount} Hinweise, ${quality.warningIssueCount} Warnungen, " +
                "${quality.criticalIssueCount} kritische Probleme. " +
                "${quality.excludedRecordCount} Einträge wurden von der Statistik ausgeschlossen."
        AppLanguage.EN ->
            "${quality.informationalIssueCount} informational, ${quality.warningIssueCount} warnings, " +
                "${quality.criticalIssueCount} critical issues. " +
                "${quality.excludedRecordCount} records were excluded from statistics."
    }
    val accessibilityText = "${S.dataHealthTitle(lang)}. " +
        "${S.dataHealthIssuesFound(quality.issueCount, lang)}. $details"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityText },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = S.dataHealthTitle(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = details,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
