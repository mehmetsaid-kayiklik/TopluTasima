package com.example.toplutasima.ui.components.personaltrip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

@Composable
internal fun PersonalTripStatusBadge(
    trip: PersonalTrip,
    lang: AppLanguage,
    pulse: Float
) {
    val durumColor = when (trip.durum) {
        PersonalTrip.DURUM_BEKLEMEDE -> Color(0xFFFFC107)
        PersonalTrip.DURUM_AKTIF -> Color(0xFF4CAF50)
        else -> Color(0xFF4CAF50)
    }
    val durumText = when (trip.durum) {
        PersonalTrip.DURUM_BEKLEMEDE -> S.personalStatusPending(lang)
        PersonalTrip.DURUM_AKTIF -> S.personalStatusActive(lang)
        else -> "✅ Tamamlandı"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(durumColor)
                    .alpha(if (trip.durum == PersonalTrip.DURUM_AKTIF) pulse else 1f)
            )
            Column {
                Text(
                    S.statusLabel(lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    durumText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
