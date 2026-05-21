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
internal data class ReportMetricModel(
    val label: String,
    val value: String
)

internal val summaryVehicleTypeEntries = listOf(
    VehicleType.BUS.key to "🚌",
    VehicleType.SBAHN.key to "🚆",
    VehicleType.UBAHN.key to "🚇",
    VehicleType.RERB.key to "🚂",
    VehicleType.FERNZUG.key to "🚄",
    VehicleType.STRASSENBAHN.key to "🚋"
)

internal fun displaySummarySheet(name: String, lang: AppLanguage): String {
    if (name == "Tümü") return S.sheetAll(lang)
    val parts = name.split(" ", limit = 2)
    return if (parts.size >= 2) "${S.monthName(parts[0], lang)} ${parts[1]}"
    else S.monthName(name, lang)
}

internal fun formatDistanceKm(value: Double): String =
    String.format(java.util.Locale.US, "%.2f km", value)

@Composable
internal fun ReportMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}