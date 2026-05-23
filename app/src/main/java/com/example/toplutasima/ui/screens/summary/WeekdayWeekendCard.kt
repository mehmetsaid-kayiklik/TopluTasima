package com.example.toplutasima.ui.screens.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.DayTypeStats
import com.example.toplutasima.model.WeekdayWeekendStats
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import java.util.Locale

@Composable
internal fun WeekdayWeekendCard(
    stats: WeekdayWeekendStats,
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                S.weekdayWeekendComparison(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DayTypeColumn(
                    title = S.weekday(lang),
                    stats = stats.weekday,
                    highlighted = stats.busiestType == "weekday",
                    lang = lang,
                    modifier = Modifier.weight(1f)
                )
                DayTypeColumn(
                    title = S.weekend(lang),
                    stats = stats.weekend,
                    highlighted = stats.busiestType == "weekend",
                    lang = lang,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayTypeColumn(
    title: String,
    stats: DayTypeStats,
    highlighted: Boolean,
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    val background = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (highlighted) "$title • ${S.busier(lang)}" else title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CompactMetric(
            label = S.tripsShort(lang),
            value = stats.trips.toString(),
            color = contentColor
        )
        CompactMetric(
            label = S.avgShort(lang),
            value = "+${String.format(Locale.US, "%.1f", stats.avgDelay)} ${S.minutesShort(lang)}",
            color = contentColor
        )
        CompactMetric(
            label = S.avgDistance(lang),
            value = formatDistanceKm(stats.avgDistanceKm),
            color = contentColor
        )
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
