package br.com.volts.driver.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.volts.driver.apps.Apps
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Mapa ao vivo com a posição do carro, fixo no canto do Painel. */
@Composable
fun MiniMap(modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleResumeEffect(Unit) {
        granted = hasLocationPermission(context)
        onPauseOrDispose { }
    }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (granted) {
            LiveMap()
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Permita a localização para ver o mapa com a sua posição.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Permitir localização") }
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun LiveMap() {
    val context = LocalContext.current
    val location by rememberLocation()
    // Segue o carro até o motorista arrastar o mapa; o botão de mira volta a seguir.
    var follow by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    val map = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS) // mapa escuro
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(-14.2, -51.9)) // Brasil, até chegar a primeira posição
            setOnTouchListener { _, _ -> follow = false; false }
        }
    }
    val marker = remember {
        Marker(map).apply {
            icon = dot(context, VoltsAmarelo, 22)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(location, follow) {
        val loc = location ?: return@LaunchedEffect
        val point = GeoPoint(loc.latitude, loc.longitude)
        marker.position = point
        if (marker !in map.overlays) map.overlays += marker
        if (!zoomed) {
            zoomed = true
            map.controller.setZoom(16.5)
            map.controller.setCenter(point)
        } else if (follow) {
            map.controller.animateTo(point)
        }
        map.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        if (location == null) {
            Text(
                "Procurando sinal de GPS…",
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                    .clip(RoundedCornerShape(50)).background(VoltsBlack.copy(alpha = 0.8f)).padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!follow) {
                FilledTonalIconButton(onClick = { follow = true }) { Icon(Icons.Filled.MyLocation, "Centralizar no carro") }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                enabled = location != null,
                onClick = {
                    location?.let { Apps.start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${it.latitude},${it.longitude}?z=16"))) }
                },
            ) {
                Icon(Icons.Filled.Navigation, null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir navegação")
            }
        }
    }
}

/** Posição atual, atualizada enquanto a tela está visível. Requer a permissão já concedida. */
@SuppressLint("MissingPermission")
@Composable
private fun rememberLocation(): State<Location?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Location?>(null) }
    LifecycleResumeEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { state.value = it }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in lm.allProviders }
        if (state.value == null) {
            state.value = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }
        providers.forEach { lm.requestLocationUpdates(it, 2_000L, 3f, listener, context.mainLooper) }
        onPauseOrDispose { lm.removeUpdates(listener) }
    }
    return state
}
