package com.example.toplutasima.ui.components.personaltrip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import java.util.Locale

@Composable
internal fun PersonalTripInfoRows(
    trip: PersonalTrip,
    liveDistanceKm: Double,
    lang: AppLanguage,
    pulse: Float
) {
    when (trip.durum) {
        PersonalTrip.DURUM_BEKLEMEDE -> {
            Text(
                "${S.personalFrom(lang)}: —\n${S.personalTo(lang)}: —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PersonalTrip.DURUM_AKTIF -> {
            if (trip.kaldigiYer.isNotBlank()) {
                Text(
                    "${trip.kaldigiSaat}  ${trip.kaldigiYer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(pulse)
            ) {
                Text(
                    String.format(Locale.US, "%.1f km", liveDistanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
        else -> {
            if (trip.kaldigiYer.isNotBlank() || trip.kaldigiSaat.isNotBlank()) {
                Text(
                    "${trip.kaldigiSaat}  ${trip.kaldigiYer.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (trip.varisYeri.isNotBlank() || trip.varisSaat.isNotBlank()) {
                Text(
                    "${trip.varisSaat}  ${trip.varisYeri.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trip.mesafe.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(trip.mesafe, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (trip.yolSuresi.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("${trip.yolSuresi} ${S.minutesShort(lang)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (trip.not.isNotBlank()) {
        Text(
            "${trip.not.lines().first()}${if (trip.not.contains('\n')) "…" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
