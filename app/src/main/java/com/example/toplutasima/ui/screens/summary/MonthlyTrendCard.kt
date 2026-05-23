package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.MonthlyTrendData
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SubtleGray
import com.example.toplutasima.ui.Teal
import com.example.toplutasima.ui.TealLight

@Composable
fun MonthlyTrendCard(
    trendData: List<MonthlyTrendData>,
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    if (trendData.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = S.monthlyTrendTitle(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Find maximum trips for scaling
            val maxTrips = trendData.maxOfOrNull { it.trips }?.coerceAtLeast(1) ?: 1

            // Graph area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                trendData.forEach { data ->
                    val isBusiest = data.trips == maxTrips && maxTrips > 0
                    val barColor = if (isBusiest) TealLight else Teal

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Trip count label on top of bar
                        Text(
                            text = "${data.trips}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isBusiest) TealLight else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Individual Bar drawn with Compose Canvas
                        Canvas(
                            modifier = Modifier
                                .width(20.dp)
                                .weight(1f)
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            
                            // Scale height relative to maxTrips
                            val barHeight = (data.trips.toFloat() / maxTrips) * canvasHeight

                            val left = 0f
                            val top = canvasHeight - barHeight

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, top),
                                size = Size(canvasWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Month short name label
                        Text(
                            text = data.monthName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SubtleGray,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        // Distance label
                        Text(
                            text = "${Math.round(data.distanceKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.sp,
                            color = SubtleGray.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
