package br.com.volts.driver.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.volts.driver.apps.Apps
import br.com.volts.driver.apps.rememberInstalledApps
import br.com.volts.driver.offers.OfferAccessibilityService
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = app().prefs
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val installed by rememberInstalledApps()
    var picker by remember { mutableStateOf<String?>(null) }

    var offersEnabled by remember { mutableStateOf(false) }
    var isHome by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        offersEnabled = isOfferServiceEnabled(context)
        isHome = isDefaultHome(context)
        onPauseOrDispose { }
    }

    when (picker) {
        "panel" -> AppPickerDialog(
            "Apps do painel", installed.orEmpty(), onDismiss = { picker = null },
            trailing = { entry ->
                if (entry.pkg in settings.pinned) {
                    Text("Ler ofertas", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp))
                    Switch(
                        checked = entry.pkg in settings.monitored,
                        onCheckedChange = { on -> prefs.update { it.copy(monitored = if (on) it.monitored + entry.pkg else it.monitored - entry.pkg) } },
                    )
                }
                Checkbox(checked = entry.pkg in settings.pinned, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
            },
        ) { entry ->
            prefs.update {
                if (entry.pkg in it.pinned) it.copy(pinned = it.pinned - entry.pkg, monitored = it.monitored - entry.pkg)
                else it.copy(pinned = it.pinned + entry.pkg)
            }
        }
        "acquirer" -> AppPickerDialog("App de pagamento por aproximação", installed.orEmpty(), onDismiss = { picker = null }) { entry ->
            prefs.update { it.copy(acquirerPkg = entry.pkg) }
            picker = null
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge)

        Section("Tablet") {
            ActionRow(
                "Tela inicial",
                if (isHome) "O Volts Driver é a tela inicial: o tablet já liga no painel."
                else "Defina o Volts Driver como app de início para o tablet ligar direto no painel.",
                "Definir",
            ) { Apps.start(context, Intent(Settings.ACTION_HOME_SETTINGS)) }
            ActionRow("Apps do painel", "${settings.pinned.size} apps fixados", "Escolher") { picker = "panel" }
        }

        Section("Relatório") {
            ActionRow(
                "Leitura de ofertas",
                if (offersEnabled) "Ativa: as ofertas dos apps de corrida escolhidos entram no relatório."
                else "Desativada. Ative o serviço \"Volts Driver – Leitura de ofertas\" em Acessibilidade.",
                if (offersEnabled) "Gerenciar" else "Ativar",
            ) { Apps.start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        Section("Recebimento") {
            OutlinedTextField(settings.pixKey, { v -> prefs.update { it.copy(pixKey = v) } },
                label = { Text("Chave Pix (CPF, celular com +55, e-mail ou aleatória)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(settings.pixName, { v -> prefs.update { it.copy(pixName = v) } },
                    label = { Text("Nome do recebedor") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(settings.pixCity, { v -> prefs.update { it.copy(pixCity = v) } },
                    label = { Text("Cidade") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            ActionRow(
                "Aproximação (NFC)",
                installed?.firstOrNull { it.pkg == settings.acquirerPkg }?.label?.let { "Abre o app $it para o passageiro aproximar o cartão." }
                    ?: "Escolha o app da sua adquirente com Tap to Pay.",
                "Escolher",
            ) { picker = "acquirer" }
        }

        Section("Postos") {
            Text("Raio de busca: ${settings.radiusKm} km")
            Slider(
                value = settings.radiusKm.toFloat(),
                onValueChange = { v -> prefs.update { it.copy(radiusKm = v.roundToInt()) } },
                valueRange = 1f..20f,
                steps = 18,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) { Text(button) }
    }
}

private fun isOfferServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, OfferAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

private fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
}
