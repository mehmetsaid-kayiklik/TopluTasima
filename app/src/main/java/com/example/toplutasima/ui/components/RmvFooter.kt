package com.example.toplutasima.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RmvFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logoBitmap = remember {
        try {
            val stream = context.assets.open("rmv_logo.png")
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            bmp?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "RMV Logo",
                    modifier = Modifier.height(24.dp),
                    contentScale = ContentScale.FillHeight
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "Datenquelle: RMV",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
