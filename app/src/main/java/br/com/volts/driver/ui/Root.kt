package br.com.volts.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.volts.driver.R
import br.com.volts.driver.VoltsApp

private class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Painel", Icons.Filled.Dashboard),
    Tab("Postos", Icons.Filled.EvStation),
    Tab("Cobrar", Icons.Filled.Payments),
    Tab("Relatório", Icons.Filled.BarChart),
    Tab("Ajustes", Icons.Filled.Settings),
)

@Composable
fun app(): VoltsApp = LocalContext.current.applicationContext as VoltsApp

@Composable
fun VoltsRoot() {
    VoltsTheme {
        Surface(Modifier.fillMaxSize()) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            val content: @Composable (Modifier, Boolean) -> Unit = { m, wide ->
                Box(m.padding(16.dp)) {
                    when (tab) {
                        0 -> HomeScreen(showLogo = !wide)
                        1 -> StationsScreen()
                        2 -> PayScreen(onOpenSettings = { tab = 4 })
                        3 -> ReportScreen()
                        else -> SettingsScreen()
                    }
                }
            }
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                if (maxWidth >= 600.dp) {
                    Row {
                        NavigationRail(
                            header = {
                                Image(painterResource(R.drawable.volts_simbolo), "Volts Brasil", Modifier.padding(vertical = 12.dp).size(40.dp))
                            },
                        ) {
                            tabs.forEachIndexed { i, t ->
                                NavigationRailItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                        content(Modifier.weight(1f), true)
                    }
                } else {
                    Column {
                        content(Modifier.weight(1f), false)
                        NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label, maxLines = 1) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
