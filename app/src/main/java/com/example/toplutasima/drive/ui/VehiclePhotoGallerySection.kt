package com.example.toplutasima.drive.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.toplutasima.drive.photo.DriveVehiclePhoto
import com.example.toplutasima.drive.photo.VehiclePhotoUploadState
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import java.io.File
import java.util.UUID

@Composable
internal fun VehiclePhotoGallerySection(
    state: VehiclePhotoUiState,
    onAdd: (Uri) -> Unit,
    onDelete: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRetry: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language = LocaleManager.currentLanguage
    var deleteCandidate by remember { mutableStateOf<DriveVehiclePhoto?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onAdd)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingCameraUri?.let(onAdd)
        pendingCameraUri = null
    }

    state.message?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message.text(language), android.widget.Toast.LENGTH_LONG).show()
            onMessageShown()
        }
    }

    Card(modifier = modifier.fillMaxWidth().testTag(DriveUiTestTags.PHOTO_SECTION)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.drivePhotos(language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !state.working,
                    modifier = Modifier.testTag(DriveUiTestTags.PHOTO_ADD)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = S.driveAddPhoto(language))
                }
                IconButton(
                    onClick = {
                        val directory = File(context.cacheDir, "vehicle_photo_sources").apply { mkdirs() }
                        val file = File(directory, "${UUID.randomUUID()}.jpg")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    enabled = !state.working
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = S.driveTakePhoto(language))
                }
            }
            when {
                state.loading -> CircularProgressIndicator()
                state.photos.isEmpty() -> Text(
                    S.driveNoPhotos(language),
                    modifier = Modifier.testTag(DriveUiTestTags.PHOTO_EMPTY)
                )
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.photos, key = { _, photo -> photo.photoId }) { index, photo ->
                        VehiclePhotoCard(
                            photo = photo,
                            language = language,
                            canMoveLeft = index > 0,
                            canMoveRight = index < state.photos.lastIndex,
                            working = state.working,
                            onDelete = { deleteCandidate = photo },
                            onSetPrimary = { onSetPrimary(photo.photoId) },
                            onMove = { direction -> onMove(photo.photoId, direction) },
                            onRetry = { onRetry(photo.photoId) }
                        )
                    }
                }
            }
        }
    }

    deleteCandidate?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(S.driveDeletePhoto(language)) },
            text = { Text(S.driveDeletePhoto(language) + "?") },
            confirmButton = {
                Button(onClick = {
                    deleteCandidate = null
                    onDelete(photo.photoId)
                }) { Text(S.driveDeletePhoto(language)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteCandidate = null }) { Text(S.cancel(language)) }
            }
        )
    }
}

@Composable
private fun VehiclePhotoCard(
    photo: DriveVehiclePhoto,
    language: AppLanguage,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    working: Boolean,
    onDelete: () -> Unit,
    onSetPrimary: () -> Unit,
    onMove: (Int) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.width(240.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val file = photo.localPreparedPath?.let(::File)?.takeIf(File::isFile)
            if (file != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey(photo.cacheKey())
                        .diskCacheKey(photo.cacheKey())
                        .build(),
                    contentDescription = if (photo.isPrimary) S.drivePrimaryPhoto(language) else S.drivePhotos(language),
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo.healthCode != null) Text(S.drivePhotoFailed(language))
                    else CircularProgressIndicator()
                }
            }
            if (photo.isPrimary) {
                Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Text(S.drivePrimaryPhoto(language))
                }
            }
            Text(photo.statusText(language), modifier = Modifier.padding(horizontal = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveLeft && !working) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveRight && !working) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                }
                if (!photo.isPrimary) {
                    IconButton(
                        onClick = onSetPrimary,
                        enabled = !working,
                        modifier = Modifier.testTag(DriveUiTestTags.PHOTO_PRIMARY)
                    ) { Icon(Icons.Default.Star, contentDescription = S.driveMakePrimary(language)) }
                }
                IconButton(onClick = onDelete, enabled = !working) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = S.driveDeletePhoto(language))
                }
            }
            if (photo.uploadState == VehiclePhotoUploadState.RETRYABLE_ERROR ||
                photo.uploadState == VehiclePhotoUploadState.FATAL_ERROR
            ) {
                TextButton(
                    onClick = onRetry,
                    enabled = !working,
                    modifier = Modifier.testTag(DriveUiTestTags.PHOTO_RETRY)
                ) { Text(S.driveRetry(language)) }
            }
        }
    }
}

private fun DriveVehiclePhoto.cacheKey(): String =
    listOf(ownerUid, vehicleId, photoId, contentHash.orEmpty()).joinToString(":")

private fun DriveVehiclePhoto.statusText(language: AppLanguage): String = when (uploadState) {
    VehiclePhotoUploadState.SYNCED -> S.driveSynced(language)
    VehiclePhotoUploadState.RETRYABLE_ERROR,
    VehiclePhotoUploadState.FATAL_ERROR -> S.drivePhotoFailed(language)
    else -> S.drivePhotoPending(language)
}

private fun VehiclePhotoUiMessage.text(language: AppLanguage): String = when (this) {
    VehiclePhotoUiMessage.ADDED -> S.drivePhotoPending(language)
    VehiclePhotoUiMessage.DELETED -> S.driveDeletePhoto(language)
    VehiclePhotoUiMessage.PRIMARY_CHANGED -> S.drivePrimaryPhoto(language)
    VehiclePhotoUiMessage.REORDERED -> S.drivePhotos(language)
    VehiclePhotoUiMessage.RETRY_QUEUED -> S.driveSyncRetry(language)
    VehiclePhotoUiMessage.SOURCE_UNAVAILABLE -> S.drivePhotoFailed(language)
    VehiclePhotoUiMessage.TOO_LARGE -> S.drivePhotoFailed(language)
    VehiclePhotoUiMessage.UNSUPPORTED_TYPE -> S.drivePhotoFailed(language)
    VehiclePhotoUiMessage.ACCOUNT_CHANGED -> S.driveAuthRequired(language)
    VehiclePhotoUiMessage.VEHICLE_DELETED -> S.driveVehicleNotFound(language)
    VehiclePhotoUiMessage.FAILED -> S.driveUnknownFailure(language)
}
