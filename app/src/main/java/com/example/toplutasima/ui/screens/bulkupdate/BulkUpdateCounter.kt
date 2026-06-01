package com.example.toplutasima.ui.screens.bulkupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.toplutasima.ui.SuccessGreen

@Composable
private fun BulkUpdateCounter(
    icon: ImageVector,
    value: Int,
    tint: Color,
    label: String? = null,
    large: Boolean = false
) {
    val textStyle = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (large) 18.dp else 16.dp)
        )
        Text(
            text = buildString {
                append(value)
                if (label != null) append(" ").append(label)
            },
            style = textStyle,
            color = tint
        )
    }
}

@Composable
internal fun BulkUpdateSuccessCount(
    value: Int,
    label: String? = null,
    large: Boolean = false
) {
    BulkUpdateCounter(
        icon = Icons.Outlined.CheckCircle,
        value = value,
        tint = SuccessGreen,
        label = label,
        large = large
    )
}

@Composable
internal fun BulkUpdateFailureCount(
    value: Int,
    label: String? = null,
    large: Boolean = false
) {
    BulkUpdateCounter(
        icon = Icons.Outlined.Cancel,
        value = value,
        tint = MaterialTheme.colorScheme.error,
        label = label,
        large = large
    )
}
