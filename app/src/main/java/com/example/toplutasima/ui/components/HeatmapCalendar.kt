package com.example.toplutasima.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.usecase.HeatmapData
import com.example.toplutasima.usecase.HeatmapMetric
import com.example.toplutasima.usecase.HeatmapUtils
import java.time.DayOfWeek
import java.time.YearMonth

@Composable
fun HeatmapCalendar(
    data: HeatmapData,
    modifier: Modifier = Modifier,
    metric: HeatmapMetric = HeatmapMetric.TRIPS,
    lang: AppLanguage = LocaleManager.currentLanguage,
    onDayClick: ((Int, Int) -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    var tooltipDay by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val ym = YearMonth.of(data.year, data.month)
    val firstDayOfWeek = ym.atDay(1).dayOfWeek
    val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalDays = ym.lengthOfMonth()
    val totalRows = (startOffset + totalDays + 6) / 7
    val metricValues = when (metric) {
        HeatmapMetric.TRIPS -> data.dailyCounts
        HeatmapMetric.AVG_DELAY -> data.dailyAvgDelay
        HeatmapMetric.TICKET_CONTROL -> data.dailyTicketCounts
        HeatmapMetric.SEATED -> data.dailySeatedCounts
    }
    val metricMax = when (metric) {
        HeatmapMetric.TRIPS -> data.maxDailyCount
        HeatmapMetric.AVG_DELAY -> data.maxDailyAvgDelay
        HeatmapMetric.TICKET_CONTROL -> data.maxDailyTicketCount
        HeatmapMetric.SEATED -> data.maxDailySeatedCount
    }

    Column(modifier = modifier) {
        tooltipDay?.let { (day, value) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    "$day. ${S.dayUnit(lang).lowercase()} - ${S.heatmapMetricValue(metric, value, lang)}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Pt", "Sa", "Ca", "Pe", "Cu", "Ct", "Pa").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val canvasHeight = (totalRows * 40 + 8).dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .pointerInput(data, metric) {
                    detectTapGestures { offset ->
                        val cellW = size.width / 7f
                        val cellH = size.height / totalRows.toFloat()
                        val col = (offset.x / cellW).toInt()
                        val row = (offset.y / cellH).toInt()
                        val dayNum = row * 7 + col - startOffset + 1
                        if (dayNum in 1..totalDays) {
                            val value = metricValues[dayNum] ?: 0
                            tooltipDay = dayNum to value
                            onDayClick?.invoke(dayNum, value)
                        }
                    }
                }
        ) {
            val cellW = size.width / 7f
            val cellH = size.height / totalRows.toFloat()
            val cellPadding = 3f
            val cornerRadius = CornerRadius(6f, 6f)

            for (row in 0 until totalRows) {
                for (col in 0..6) {
                    val dayNum = row * 7 + col - startOffset + 1
                    val x = col * cellW + cellPadding
                    val y = row * cellH + cellPadding
                    val w = cellW - cellPadding * 2
                    val h = cellH - cellPadding * 2

                    if (dayNum in 1..totalDays) {
                        val value = metricValues[dayNum] ?: 0
                        val level = HeatmapUtils.intensityLevel(value, metricMax)
                        val cellColor = when (level) {
                            0 -> surfaceVariant
                            1 -> primaryColor.copy(alpha = 0.2f)
                            2 -> primaryColor.copy(alpha = 0.4f)
                            3 -> primaryColor.copy(alpha = 0.7f)
                            else -> primaryColor
                        }

                        drawRoundRect(
                            color = cellColor,
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            cornerRadius = cornerRadius
                        )

                        val dayText = dayNum.toString()
                        val textColor = if (level >= 3) Color.White else onSurfaceColor
                        val textResult = textMeasurer.measure(
                            dayText,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor)
                        )
                        drawText(
                            textResult,
                            topLeft = Offset(
                                x + (w - textResult.size.width) / 2f,
                                y + (h - textResult.size.height) / 2f
                            )
                        )
                    }
                }
            }
        }
    }
}
