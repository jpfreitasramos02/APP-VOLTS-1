package br.com.volts.driver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.volts.driver.apps.AppEntry
import br.com.volts.driver.apps.Apps
import br.com.volts.driver.apps.KnownApps
import br.com.volts.driver.apps.rememberInstalledApps
import br.com.volts.driver.data.Shift
import br.com.volts.driver.tracking.ShiftService
import br.com.volts.driver.util.formatDuration
import br.com.volts.driver.util.formatKm
import kotlinx.coroutines.delay

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = app()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val installed by rememberInstalledApps()
    val byPkg = remember(installed) { installed.orEmpty().associateBy { it.pkg } }
    val pinned = remember(settings.pinned, byPkg) {
        settings.pinned.sortedWith(compareBy({ KnownApps.order(it) }, { byPkg[it]?.label ?: it }))
            // apps fixados manualmente que foram desinstalados somem; os conhecidos viram "Instalar"
            .filter { it in byPkg || KnownApps.label(it) != null }
    }

    var allApps by remember { mutableStateOf(false) }
    if (allApps) {
        AppPickerDialog("Todos os apps", installed.orEmpty(), onDismiss = { allApps = false }) {
            allApps = false
            Apps.launch(context, it.pkg)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShiftCard()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Seus apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { Apps.launchAll(context, pinned.filter { it in byPkg }) },
                enabled = pinned.any { it in byPkg },
            ) { Text("Abrir todos") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { allApps = true }) { Text("Outros apps") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pinned, key = { it }) { pkg ->
                AppTile(pkg, byPkg[pkg], loading = installed == null)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(pkg: String, entry: AppEntry?, loading: Boolean) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(
                    enabled = !loading,
                    onClick = { if (entry != null) Apps.launch(context, pkg) else Apps.openStore(context, pkg) },
                    onLongClick = { if (entry != null) menu = true },
                )
                .padding(12.dp),
        ) {
            if (entry != null) {
                Image(entry.icon, null, Modifier.size(64.dp))
            } else {
                Icon(Icons.Filled.Download, null, Modifier.size(64.dp).alpha(0.4f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                entry?.label ?: KnownApps.label(pkg) ?: pkg,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (entry == null && !loading) {
                Text("Toque para instalar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Abrir em tela dividida") }, onClick = {
                menu = false
                Apps.launch(context, pkg, sideBySide = true)
            })
        }
    }
}

@Composable
private fun ShiftCard() {
    val context = LocalContext.current
    val dao = app().dao
    val shift by remember { dao.activeShift() }.collectAsStateWithLifecycle(initialValue = null)
    var denied by remember { mutableStateOf(false) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            denied = false
            ShiftService.start(context)
        } else {
            denied = true
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (shift != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val s = shift
            if (s == null) {
                Text("Turno parado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (denied) "Sem a permissão de localização precisa não dá para medir os km."
                    else "Inicie o turno para medir km rodados e horas online.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Turno em andamento", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatKm(s.distanceMeters), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(20.dp))
                    Text(formatDuration(elapsed(s)), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        if (shift == null) {
            Button(
                onClick = {
                    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (ok) ShiftService.start(context) else launcher.launch(permissions)
                },
                modifier = Modifier.height(64.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Iniciar turno", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = { ShiftService.stop(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.height(64.dp),
            ) {
                Icon(Icons.Filled.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("Encerrar", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Tempo decorrido do turno, atualizado a cada 15 s. */
@Composable
private fun elapsed(shift: Shift): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(shift.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    return now - shift.startTime
}
