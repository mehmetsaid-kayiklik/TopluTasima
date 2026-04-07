package com.example.toplutasima.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.usecase.HeatmapData
import com.example.toplutasima.usecase.HeatmapUtils
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * Canvas tabanlı heatmap takvim.
 * 7 sütun (Pzt-Paz) × 4-6 satır grid gösterir.
 */
@Composable
fun HeatmapCalendar(
    data: HeatmapData,
    modifier: Modifier = Modifier,
    onDayClick: ((Int, Int) -> Unit)? = null // gün numarası, sefer sayısı
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    var tooltipDay by remember { mutableStateOf<Pair<Int, Int>?>(null) } // day, count

    val ym = YearMonth.of(data.year, data.month)
    val firstDayOfWeek = ym.atDay(1).dayOfWeek // Pazartesi=1
    val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalDays = ym.lengthOfMonth()
    val totalRows = (startOffset + totalDays + 6) / 7

    Column(modifier = modifier) {
        // Tooltip
        if (tooltipDay != null) {
            val (day, count) = tooltipDay!!
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    "$day. gün — $count sefer",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Day header labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val dayLabels = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pa")
            dayLabels.forEach { label ->
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

        // Canvas grid
        val canvasHeight = (totalRows * 40 + 8).dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val cellW = size.width / 7f
                        val cellH = size.height / totalRows.toFloat()
                        val col = (offset.x / cellW).toInt()
                        val row = (offset.y / cellH).toInt()
                        val dayIndex = row * 7 + col - startOffset
                        val dayNum = dayIndex + 1
                        if (dayNum in 1..totalDays) {
                            val count = data.dailyCounts[dayNum] ?: 0
                            tooltipDay = Pair(dayNum, count)
                            onDayClick?.invoke(dayNum, count)
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
                    val dayIndex = row * 7 + col - startOffset
                    val dayNum = dayIndex + 1

                    val x = col * cellW + cellPadding
                    val y = row * cellH + cellPadding
                    val w = cellW - cellPadding * 2
                    val h = cellH - cellPadding * 2

                    if (dayNum in 1..totalDays) {
                        val count = data.dailyCounts[dayNum] ?: 0
                        val level = HeatmapUtils.intensityLevel(count, data.maxDailyCount)

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

                        // Draw day number
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
