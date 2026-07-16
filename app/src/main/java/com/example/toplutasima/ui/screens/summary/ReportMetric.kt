package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

internal val summaryVehicleTypeEntries = listOf(
    VehicleType.BUS.key to "",
    VehicleType.SBAHN.key to "",
    VehicleType.UBAHN.key to "",
    VehicleType.RERB.key to "",
    VehicleType.FERNZUG.key to "",
    VehicleType.STRASSENBAHN.key to ""
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
