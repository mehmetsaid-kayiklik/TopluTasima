package com.example.toplutasima.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.viewmodel.ReachabilityViewModel
import org.koin.androidx.compose.koinViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@Composable
fun ReachabilityScreen(
    modifier: Modifier = Modifier,
    viewModel: ReachabilityViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionState()
        viewModel.load(state.minutes)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                AssistChip(
                    onClick = { viewModel.load(minutes) },
                    label = { Text("${minutes} dk") }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Erisilebilirlik Haritasi", fontWeight = FontWeight.Bold)
                    Text(state.status, style = MaterialTheme.typography.bodySmall)
                }
                if (!state.hasLocationPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Izin ver")
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val result = state.result
            if (result != null) {
                MapLibreReachabilityMap(
                    originLat = result.originLat,
                    originLon = result.originLon,
                    points = result.points.map { LatLng(it.lat, it.lon) to it.name }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Haritayi baslatmak icin sure secin")
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLibreReachabilityMap(
    originLat: Double,
    originLon: Double,
    points: List<Pair<LatLng, String>>
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(BuildConfig.MAP_STYLE_URL) {
                    val origin = LatLng(originLat, originLon)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 11.5))
                    map.clear()
                    map.addMarker(MarkerOptions().position(origin).title("Konumum"))
                    points.take(40).forEach { (point, name) ->
                        map.addMarker(MarkerOptions().position(point).title(name))
                    }
                }
            }
        }
    )
}
