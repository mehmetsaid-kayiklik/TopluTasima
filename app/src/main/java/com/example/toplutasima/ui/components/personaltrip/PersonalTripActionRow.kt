package com.example.toplutasima.ui.components.personaltrip

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S

@Composable
internal fun PersonalTripActionRow(
    trip: PersonalTrip,
    lang: AppLanguage,
    onBindim: () -> Unit,
    onIndim: () -> Unit
) {
    when (trip.durum) {
        PersonalTrip.DURUM_BEKLEMEDE -> {
            Button(
                onClick = onBindim,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(S.personalBindim(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
        PersonalTrip.DURUM_AKTIF -> {
            Button(
                onClick = onIndim,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text(S.personalIndim(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
        else -> Unit
    }
}
