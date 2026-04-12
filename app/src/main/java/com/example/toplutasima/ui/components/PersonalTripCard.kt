package com.example.toplutasima.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import com.example.toplutasima.viewmodel.PersonalTripViewModel

/**
 * Kişisel araç yolculuğu kartı.
 * Duruma göre üç görsel hal sunar:
 *  - beklemede: sarı kenar + [🚗 Bindim] butonu
 *  - aktif:     yeşil nabız + canlı km sayacı + [🏁 İndim] butonu
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
    var showMenu by remember { mutableStateOf(false) }

    // Araç türü emojisi
    val vehicleEmoji = S.personalVehicleOptions.find { it.first == trip.aracTuru }?.second ?: "🚗"
    val havaEmoji    = S.weatherOptions.find { it.first == trip.havaDurumu }?.second ?: "❓"

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
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, RoundedCornerShape(16.dp)) else Modifier),
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
                    Text(vehicleEmoji, fontSize = 22.sp)
                    Column {
                        Text(
                            "${trip.tarih}  $havaEmoji",
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

                // Durum etiketi veya menü
                when (trip.durum) {
                    PersonalTrip.DURUM_BEKLEMEDE -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0)
                        ) {
                            Text(
                                S.personalStatusPending(lang),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                    PersonalTrip.DURUM_AKTIF -> {
                        Text(
                            S.personalStatusActive(lang),
                            modifier = Modifier.alpha(pulse),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    else -> {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) { Text("⋮", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("✏️  ${S.editEdit(lang)}") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.openEditDialog(trip)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🗑️  ${S.delete(lang)}", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Sürücü / Yolcu bilgisi ──────────────────────────────────────
            if (trip.surucu != null || trip.yolcuSayisi != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (trip.surucu == true) Text("🧑‍💼 Sürücü", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (trip.surucu == false) Text("🪑 Yolcu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (trip.yolcuSayisi != null) Text("👥 ${trip.yolcuSayisi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // ── Konum & zaman bilgisi ────────────────────────────────────────
            when (trip.durum) {
                PersonalTrip.DURUM_BEKLEMEDE -> {
                    Text(
                        "📍 ${S.personalFrom(lang)}: —\n📍 ${S.personalTo(lang)}: —",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PersonalTrip.DURUM_AKTIF -> {
                    // Kalkış
                    if (trip.kaldigiYer.isNotBlank()) {
                        Text(
                            "📍 ${trip.kaldigiSaat}  ${trip.kaldigiYer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Canlı mesafe
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.alpha(pulse)
                    ) {
                        Text(
                            "📏 ${String.format("%.1f km", liveDistanceKm)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                else -> {
                    // Tamamlandı
                    if (trip.kaldigiYer.isNotBlank() || trip.kaldigiSaat.isNotBlank()) {
                        Text(
                            "📍 ${trip.kaldigiSaat}  ${trip.kaldigiYer.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (trip.varisYeri.isNotBlank() || trip.varisSaat.isNotBlank()) {
                        Text(
                            "🏁 ${trip.varisSaat}  ${trip.varisYeri.ifBlank { "—" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Özet çip'leri
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (trip.mesafe.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("📏 ${trip.mesafe}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (trip.yolSuresi.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("⏱️ ${trip.yolSuresi} ${S.minutesShort(lang)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Not
            if (trip.not.isNotBlank()) {
                Text(
                    "💬 ${trip.not.lines().first()}${if (trip.not.contains('\n')) "…" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Aksiyon Butonu ───────────────────────────────────────────────
            when (trip.durum) {
                PersonalTrip.DURUM_BEKLEMEDE -> {
                    Button(
                        onClick = { viewModel.recordBindim(context, trip.firestoreDocId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(S.personalBindim(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                }
                PersonalTrip.DURUM_AKTIF -> {
                    Button(
                        onClick = { viewModel.recordIndim(context, trip.firestoreDocId) },
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
