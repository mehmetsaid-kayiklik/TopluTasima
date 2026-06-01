package com.example.toplutasima.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.components.personaltrip.PersonalTripActionRow
import com.example.toplutasima.ui.components.personaltrip.PersonalTripInfoRows
import com.example.toplutasima.ui.components.personaltrip.PersonalTripStatusBadge
import com.example.toplutasima.viewmodel.PersonalTripViewModel

/**
 * Kişisel araç yolculuğu kartı.
 * Duruma göre üç görsel hal sunar:
 *  - beklemede: sarı kenar + [Bindim] butonu
 *  - aktif:     yeşil nabız + canlı km sayacı + [İndim] butonu
 *  - tamamlandi: normal kart, 3-nokta menü
 */
@Composable
fun PersonalTripCard(
    trip: PersonalTrip,
    liveDistanceKm: Double,
    lang: AppLanguage,
    viewModel: PersonalTripViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val bindimPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.recordBindim(context, trip.firestoreDocId)
        } else {
            viewModel.recordBindim(context, trip.firestoreDocId)
        }
    }

    // Nabız animasyonu (aktif durum için)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    // Kenar rengi
    val borderColor by animateColorAsState(
        targetValue = when (trip.durum) {
            PersonalTrip.DURUM_BEKLEMEDE  -> Color(0xFFFFC107)
            PersonalTrip.DURUM_AKTIF      -> Color(0xFF4CAF50)
            else                           -> Color.Transparent
        },
        label = "border"
    )

    val borderWidth = if (trip.durum == PersonalTrip.DURUM_TAMAMLANDI) 0.dp else 2.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(16.dp)) else Modifier)
            .then(
                if (trip.durum == PersonalTrip.DURUM_TAMAMLANDI)
                    Modifier.clickable { viewModel.openEditDialog(trip) }
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Üst satır: tarih + hava + araç + plaka + menü ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            trip.tarih,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                trip.aracTuru,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (trip.plaka.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        trip.plaka,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Tamamlandı kartları tıklanabilir (edit dialog açılır)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            PersonalTripStatusBadge(
                trip = trip,
                lang = lang,
                pulse = pulse
            )

            PersonalTripInfoRows(
                trip = trip,
                liveDistanceKm = liveDistanceKm,
                lang = lang,
                pulse = pulse
            )

            PersonalTripActionRow(
                trip = trip,
                lang = lang,
                onBindim = {
                    if (viewModel.hasLocationPermission()) {
                        viewModel.recordBindim(context, trip.firestoreDocId)
                    } else {
                        bindimPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                onIndim = { viewModel.recordIndim(context, trip.firestoreDocId) }
            )
        }
    }

    // ── Silme onay diyaloğu ──────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(S.delete(lang), fontWeight = FontWeight.Bold) },
            text = { Text(S.personalDeleteConfirm(lang)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTrip(trip.firestoreDocId)
                    }
                ) { Text(S.delete(lang), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(S.cancel(lang)) }
            }
        )
    }
}
