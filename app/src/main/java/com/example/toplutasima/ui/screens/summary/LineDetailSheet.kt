package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.ui.AccentBlue
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.ErrorRed
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.SuccessGreen
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.usecase.LineDetailStats
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineDetailSheet(
    detail: LineDetailStats,
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${detail.line} • %${detail.punctualityRate}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${detail.trips} ${S.tripsShort(lang)} • " +
                        "${S.avgShort(lang)} +${String.format(Locale.US, "%.1f", detail.avgDelay)} ${S.minutesShort(lang)} • " +
                        "${S.maxShort(lang)} +${detail.maxDelay} ${S.minutesShort(lang)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LineDetailSectionTitle(S.delayDistribution(lang))
            val bucketMax = detail.delayBuckets.maxOfOrNull { it.count }?.toFloat() ?: 1f
            detail.delayBuckets.forEach { bucket ->
                LineDetailProgressRow(
                    label = S.delayBucketName(bucket.key, lang),
                    value = bucket.count.toString(),
                    progress = if (bucketMax > 0f) bucket.count / bucketMax else 0f,
                    color = when (bucket.key) {
                        "early" -> AccentBlue
                        "zero" -> SuccessGreen
                        "low" -> MaterialTheme.colorScheme.primary
                        "medium" -> WarningAmber
                        else -> ErrorRed
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            LineDetailSectionTitle(S.lineDelayByTime(lang))
            detail.timeDelayStats.forEach { slot ->
                LineDetailValueRow(
                    label = S.timeSlotName(slot.key, lang),
                    value = "+${String.format(Locale.US, "%.1f", slot.avgDelay)} ${S.minutesShort(lang)}",
                    supporting = "${slot.trips} ${S.tripsShort(lang)}"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            LineDetailSectionTitle(S.lineMostDelayedDays(lang))
            if (detail.delayedDays.isEmpty()) {
                Text(
                    S.lineNoDelayedDays(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                detail.delayedDays.forEach { day ->
                    LineDetailValueRow(
                        label = day.label,
                        value = "+${day.totalDelay} ${S.minutesShort(lang)}",
                        supporting = "${day.trips} ${S.tripsShort(lang)} • " +
                            "${S.avgShort(lang)} +${String.format(Locale.US, "%.1f", day.avgDelay)} ${S.minutesShort(lang)}"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LineDetailSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun LineDetailProgressRow(
    label: String,
    value: String,
    progress: Float,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun LineDetailValueRow(
    label: String,
    value: String,
    supporting: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
