package br.com.volts.driver.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.volts.driver.apps.Apps
import br.com.volts.driver.stations.Station
import br.com.volts.driver.stations.StationType
import br.com.volts.driver.stations.Stations
import br.com.volts.driver.util.formatDistance
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.coroutines.resume

private sealed interface Load {
    data object NeedsPermission : Load
    data object Loading : Load
    data class Failed(val message: String) : Load
    data class Ready(val lat: Double, val lon: Double, val stations: List<Station>) : Load
}

@Composable
fun StationsScreen() {
    val context = LocalContext.current
    val settings by app().prefs.settings.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf<Load>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var showFuel by rememberSaveable { mutableStateOf(true) }
    var showEv by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Station?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }

    LaunchedEffect(reload, settings.radiusKm) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            state = Load.NeedsPermission
            return@LaunchedEffect
        }
        state = Load.Loading
        val loc = currentLocation(context)
        state = if (loc == null) {
            Load.Failed("Não foi possível obter sua localização. Verifique se o GPS está ligado.")
        } else try {
            Load.Ready(loc.latitude, loc.longitude, Stations.fetchNearby(loc.latitude, loc.longitude, settings.radiusKm * 1000))
        } catch (e: Exception) {
            Load.Failed("Não foi possível buscar os postos. Verifique a conexão com a internet.")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Postos próximos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilterChip(showFuel, { showFuel = !showFuel }, label = { Text("Combustível") },
                leadingIcon = { Icon(Icons.Filled.LocalGasStation, null, Modifier.size(18.dp), tint = FuelOrange) })
            FilterChip(showEv, { showEv = !showEv }, label = { Text("Elétrico") },
                leadingIcon = { Icon(Icons.Filled.EvStation, null, Modifier.size(18.dp), tint = EvBlue) })
            IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Atualizar") }
        }
        when (val s = state) {
            Load.NeedsPermission -> Message("Para mostrar os postos perto de você, permita o acesso à localização.") {
                Button(onClick = { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Permitir localização") }
            }
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Failed -> Message(s.message) { Button(onClick = { reload++ }) { Text("Tentar de novo") } }
            is Load.Ready -> {
                val visible = remember(s, showFuel, showEv) {
                    s.stations.filter { if (it.type == StationType.FUEL) showFuel else showEv }
                }
                if (visible.isEmpty()) {
                    Message("Nenhum posto encontrado em ${settings.radiusKm} km. Aumente o raio de busca em Ajustes.") {}
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyColumn(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible, key = { "${it.type}${it.id}" }) { st ->
                            StationRow(st, selected = st == selected, onClick = { selected = st })
                        }
                    }
                    StationsMap(s.lat, s.lon, visible, selected, onSelect = { selected = it },
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)))
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        action()
    }
}

private fun stationColor(s: Station): Color = when {
    s.isVolts -> VoltsGreen
    s.type == StationType.EV -> EvBlue
    else -> FuelOrange
}

@Composable
private fun StationRow(station: Station, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            if (station.type == StationType.EV) Icons.Filled.EvStation else Icons.Filled.LocalGasStation,
            null, tint = stationColor(station), modifier = Modifier.size(32.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(station.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(formatDistance(station.distanceM), station.details).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalIconButton(onClick = { Apps.navigateTo(context, station.lat, station.lon, station.name) }) {
            Icon(Icons.Filled.Navigation, "Navegar até ${station.name}")
        }
    }
}

@Composable
private fun StationsMap(
    lat: Double, lon: Double, stations: List<Station>, selected: Station?,
    onSelect: (Station) -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.5)
            controller.setCenter(GeoPoint(lat, lon))
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(stations) {
        map.overlays.clear()
        map.overlays += Marker(map).apply {
            position = GeoPoint(lat, lon)
            icon = dot(context, Color.White, 14)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Você está aqui"
        }
        stations.forEach { st ->
            map.overlays += Marker(map).apply {
                position = GeoPoint(st.lat, st.lon)
                icon = dot(context, stationColor(st), if (st.isVolts) 26 else 20)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = st.name
                snippet = st.details
                setOnMarkerClickListener { m, _ -> onSelect(st); m.showInfoWindow(); true }
            }
        }
        map.invalidate()
    }
    LaunchedEffect(selected) {
        selected?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) }
    }
    AndroidView(factory = { map }, modifier = modifier)
}

private fun dot(context: Context, color: Color, sizeDp: Int): BitmapDrawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.BLACK
    c.drawCircle(px / 2f, px / 2f, px / 2f, p)
    p.color = color.toArgb()
    c.drawCircle(px / 2f, px / 2f, px / 2f - px * 0.12f, p)
    return BitmapDrawable(context.resources, bmp)
}

/** Posição atual; cai para a última conhecida se o GPS demorar. Requer a permissão já concedida. */
@Suppress("MissingPermission")
private suspend fun currentLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
        runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
    }
    for (provider in providers) {
        val fresh = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        if (fresh != null) return fresh
    }
    return providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
}
