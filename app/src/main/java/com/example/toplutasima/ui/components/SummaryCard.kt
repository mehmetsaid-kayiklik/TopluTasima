package com.example.toplutasima.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.theme.SurfaceD2
import com.example.toplutasima.ui.theme.SurfaceL2
import com.example.toplutasima.ui.theme.TextHighDark
import com.example.toplutasima.ui.theme.TextHighLight
import com.example.toplutasima.ui.theme.TextMidDark
import com.example.toplutasima.ui.theme.TextMidLight

@Composable
private fun isDark() = isSystemInDarkTheme()

@Composable
fun SummaryCard(title: String, value: String) {
    val dark = isDark()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) SurfaceD2 else SurfaceL2)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextMidDark else TextMidLight
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (dark) TextHighDark else TextHighLight
            )
        }
    }
}

fun formatMin(totalMinutes: Int, lang: AppLanguage = AppLanguage.TR): String {
    if (totalMinutes == 0) return "0 ${S.minutesShort(lang)}"
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val mins = totalMinutes % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("$days ${S.dayUnit(lang)}")
    if (hours > 0) parts.add("$hours ${S.hourUnit(lang)}")
    if (mins > 0) parts.add("$mins ${S.minutesShort(lang)}")
    return parts.joinToString(", ")
}
