package com.example.toplutasima.ui.screens.records

import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.WarningAmber
import com.example.toplutasima.ui.components.AddPersonalTripDialog
import com.example.toplutasima.ui.components.PersonalTripCard
import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.usecase.ExportFormat
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.usecase.TransitTimeUtils
import com.example.toplutasima.viewmodel.PersonalTripViewModel
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripUiState
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
@Composable
internal fun TripCard(trip: RecordRowUiModel, onClick: () -> Unit) {
    val lang = LocaleManager.currentLanguage
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "${trip.typeDisplay} ${trip.line} (${trip.direction})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Show delay if present
                val delayNum = trip.delay.toIntOrNull() ?: 0
                if (delayNum > 0) {
                    Text(
                        "+${delayNum}dk",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(110.dp)) {
                    Text(
                        "${trip.plannedDep} → ${trip.plannedArr}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (trip.actualDep.isNotBlank() || trip.actualArr.isNotBlank()) {
                        val delayNum = trip.delay.toIntOrNull() ?: 0
                        val actualColor = if (delayNum > 0) MaterialTheme.colorScheme.error
                                          else androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        Text(
                            "${trip.actualDep} → ${trip.actualArr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = actualColor
                        )
                    }
                }
                Text(
                    "${trip.boardingStop} → ${trip.alightingStop}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (trip.stopCount.isNotBlank() || trip.distance.isNotBlank() || trip.plannedDuration.isNotBlank() || trip.actualDuration.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val extraInfo = mutableListOf<String>()
                if (trip.stopCount.isNotBlank()) extraInfo.add("${trip.stopCount} durak")
                if (trip.distance.isNotBlank()) extraInfo.add(trip.distance)
                
                if (trip.plannedDuration.isNotBlank() && trip.plannedDuration != "0") {
                    extraInfo.add("${S.plannedDurationLabel(lang)}: ${trip.plannedDuration} dk")
                }
                if (trip.actualDuration.isNotBlank() && trip.actualDuration != "0") {
                    extraInfo.add("${S.actualDurationLabel(lang)}: ${trip.actualDuration} dk")
                }

                Text(
                    extraInfo.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trip.profileName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "👤 ${trip.profileName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (trip.seatmateNote.isNotBlank()) {
                            Text(
                                text = "(${trip.seatmateNote})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
