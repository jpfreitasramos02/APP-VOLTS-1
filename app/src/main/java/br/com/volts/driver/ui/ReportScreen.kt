package br.com.volts.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.volts.driver.apps.KnownApps
import br.com.volts.driver.apps.rememberInstalledApps
import br.com.volts.driver.util.formatBrl
import br.com.volts.driver.util.formatDecimal
import br.com.volts.driver.util.formatDuration
import br.com.volts.driver.util.formatKm
import java.util.Calendar

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val DAY_MS = 86_400_000L
private val periods = listOf("Hoje" to 0, "7 dias" to 6, "30 dias" to 29)

@Composable
fun ReportScreen() {
    val dao = app().dao
    var period by rememberSaveable { mutableIntStateOf(0) }
    val since = remember(period) { startOfToday() - periods[period].second * DAY_MS }
    val shifts by remember(since) { dao.shiftsSince(since) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val offers by remember(since) { dao.offersSince(since) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by remember(since) { dao.paymentsSince(since) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val installed by rememberInstalledApps()

    val meters = shifts.sumOf { it.distanceMeters }
    val online = shifts.sumOf { (it.endTime ?: System.currentTimeMillis()) - it.startTime }
    val byApp = remember(offers) {
        offers.groupBy { it.pkg }.map { (pkg, list) -> AppStats(pkg, list.size, list.sumOf { it.valueCents }, list.sumOf { it.km }) }
            .sortedByDescending { it.count }
    }
    val maxCount = byApp.maxOfOrNull { it.count } ?: 1

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Desempenho", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            periods.forEachIndexed { i, (label, _) ->
                FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Km rodados", formatKm(meters).removeSuffix(" km"), "km", Modifier.weight(1f))
            Stat("Tempo online", formatDuration(online), "", Modifier.weight(1f))
            Stat("Ofertas recebidas", "${offers.size}", "", Modifier.weight(1f))
            Stat("Recebido", formatBrl(payments.sumOf { it.amountCents }), "", Modifier.weight(1f))
        }

        Text("Ofertas por app", style = MaterialTheme.typography.titleMedium)
        if (byApp.isEmpty()) {
            Text(
                "Nenhuma oferta registrada neste período. Ative a leitura de ofertas em Ajustes e deixe os apps de corrida abertos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderRow()
                byApp.forEach { s ->
                    val label = installed?.firstOrNull { it.pkg == s.pkg }?.label ?: KnownApps.label(s.pkg) ?: s.pkg
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.4f))
                            Cell("${s.count}")
                            Cell(formatBrl(s.totalCents / s.count))
                            Cell(if (s.totalKm > 0) "R$ " + formatDecimal(s.totalCents / 100.0 / s.totalKm) else "–")
                            Cell(formatBrl(s.totalCents))
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(s.count / maxCount.toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

private class AppStats(val pkg: String, val count: Int, val totalCents: Long, val totalKm: Double)

@Composable
private fun HeaderRow() {
    Row {
        Text("App", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("Ofertas", "Valor médio", "Média por km", "Total ofertado").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String) {
    Text(text, Modifier.weight(1f), textAlign = TextAlign.End)
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
