package com.example.toplutasima.ui.screens.settings

import android.text.InputType
import android.widget.EditText
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement

@Composable
internal fun SecurePasswordDialog(
    title: String,
    desc: String,
    hint: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var editTextRef by remember { mutableStateOf<EditText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            this.hint = hint
                            requestFocus()
                            editTextRef = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    update = { }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val et = editTextRef
                    if (et != null) {
                        val textLength = et.text.length
                        if (textLength >= 4) {
                            val passwordChars = CharArray(textLength)
                            et.text.getChars(0, textLength, passwordChars, 0)
                            // Overwrite character buffer in EditText to prevent sensitive data lingering in memory
                            val editable = et.text
                            for (i in 0 until textLength) {
                                editable.replace(i, i + 1, "0")
                            }
                            editable.clear()
                            onConfirm(passwordChars)
                        }
                    }
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val et = editTextRef
                if (et != null) {
                    val editable = et.text
                    val textLength = editable.length
                    for (i in 0 until textLength) {
                        editable.replace(i, i + 1, "0")
                    }
                    editable.clear()
                }
                onDismiss()
            }) {
                Text(cancelLabel)
            }
        }
    )
}