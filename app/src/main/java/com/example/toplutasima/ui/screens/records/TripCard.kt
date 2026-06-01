package com.example.toplutasima.ui.screens.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.theme.DangerRed
import com.example.toplutasima.ui.theme.SurfaceCard
import com.example.toplutasima.ui.theme.SurfaceElevated
import com.example.toplutasima.ui.theme.TextMuted
import com.example.toplutasima.ui.theme.TextPrimary
import com.example.toplutasima.ui.theme.TextSecondary
import com.example.toplutasima.ui.theme.TransitBlue
import com.example.toplutasima.ui.theme.TransitBlueDim
import com.example.toplutasima.viewmodel.records.RecordRowUiModel

@Composable
internal fun TripCard(trip: RecordRowUiModel, onClick: () -> Unit) {
    val lang = LocaleManager.currentLanguage
    val delayNum = trip.delay.toIntOrNull() ?: 0
    val lineLabel = trip.line.ifBlank { trip.typeDisplay.ifBlank { "-" } }
    val vehicleLabel = trip.typeDisplay.ifBlank { trip.type }
    val plannedWindow = timeWindow(trip.plannedDep, trip.plannedArr)
    val actualWindow = timeWindow(trip.actualDep, trip.actualArr)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(TransitBlue)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = lineLabel,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vehicleLabel.isNotBlank() && vehicleLabel != lineLabel) {
                                TransitChip(
                                    text = vehicleLabel,
                                    icon = Icons.Outlined.DirectionsBus,
                                    contentColor = TransitBlue,
                                    containerColor = TransitBlueDim
                                )
                            }
                        }
                        if (trip.direction.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = trip.direction,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        if (plannedWindow.isNotBlank()) {
                            Text(
                                text = plannedWindow,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (actualWindow.isNotBlank() && actualWindow != plannedWindow) {
                            Text(
                                text = actualWindow,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (delayNum != 0) {
                            Spacer(modifier = Modifier.height(5.dp))
                            TransitChip(
                                text = if (delayNum > 0) "+${delayNum}dk" else "${delayNum}dk",
                                contentColor = if (delayNum > 0) DangerRed else TransitBlue,
                                containerColor = if (delayNum > 0) DangerRed.copy(alpha = 0.14f) else TransitBlueDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.45f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StopBlock(
                        label = S.boardingStop(lang),
                        value = trip.boardingStop,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = TransitBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    StopBlock(
                        label = S.alightingStop(lang),
                        value = trip.alightingStop,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (trip.stopCount.isNotBlank()) {
                        StatItem(
                            icon = Icons.Outlined.Route,
                            text = "${trip.stopCount} durak",
                            color = TextSecondary
                        )
                    }
                    if (trip.distance.isNotBlank()) {
                        StatItem(
                            icon = Icons.Outlined.Straighten,
                            text = trip.distance,
                            color = TextSecondary
                        )
                    }
                }

                if (trip.plannedDuration.isNotBlank() && trip.plannedDuration != "0") {
                    Spacer(modifier = Modifier.height(6.dp))
                    StatItem(
                        icon = Icons.Outlined.AccessTime,
                        text = "${S.plannedDurationLabel(lang)}: ${trip.plannedDuration} dk",
                        color = TextSecondary
                    )
                }
                if (trip.actualDuration.isNotBlank() && trip.actualDuration != "0") {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatItem(
                        icon = Icons.Outlined.AccessTime,
                        text = "${S.actualDurationLabel(lang)}: ${trip.actualDuration} dk",
                        color = TextSecondary
                    )
                }

                if (trip.profileName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val profileText = if (trip.seatmateNote.isBlank()) {
                        trip.profileName
                    } else {
                        "${trip.profileName} (${trip.seatmateNote})"
                    }
                    TransitChip(
                        text = profileText,
                        icon = Icons.Outlined.Person,
                        contentColor = TransitBlue,
                        containerColor = SurfaceElevated
                    )
                }
            }
        }
    }
}

private fun timeWindow(start: String, end: String): String =
    listOf(start, end)
        .filter { it.isNotBlank() }
        .joinToString(" - ")

@Composable
private fun StopBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransitChip(
    text: String,
    contentColor: Color,
    containerColor: Color,
    icon: ImageVector? = null
) {
    Surface(color = containerColor, shape = RoundedCornerShape(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
