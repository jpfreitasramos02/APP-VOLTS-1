package br.com.volts.driver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.volts.driver.apps.Apps
import br.com.volts.driver.data.Payment
import br.com.volts.driver.pay.PixPayload
import br.com.volts.driver.pay.qrBitmap
import br.com.volts.driver.util.formatBrl
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_CENTS = 999_999L

@Composable
fun PayScreen(onOpenSettings: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    var cents by rememberSaveable { mutableLongStateOf(0L) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val since = remember { startOfToday() }
    val payments by remember { app.dao.paymentsSince(since) }.collectAsStateWithLifecycle(initialValue = emptyList())

    fun confirm(method: String) {
        val amount = cents
        scope.launch { app.dao.insertPayment(Payment(time = System.currentTimeMillis(), amountCents = amount, method = method)) }
        cents = 0
        dialog = null
    }

    when (dialog) {
        Payment.PIX -> PixDialog(cents, settings.pixKey, settings.pixName, settings.pixCity,
            onDismiss = { dialog = null }, onConfirm = { confirm(Payment.PIX) }, onOpenSettings = { dialog = null; onOpenSettings() })
        Payment.NFC -> NfcDialog(cents, settings.acquirerPkg,
            onDismiss = { dialog = null }, onConfirm = { confirm(Payment.NFC) }, onOpenSettings = { dialog = null; onOpenSettings() })
        Payment.CASH -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Dinheiro") },
            text = { Text("Registrar ${formatBrl(cents)} recebidos em dinheiro?") },
            confirmButton = { Button(onClick = { confirm(Payment.CASH) }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Valor a cobrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatBrl(cents), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Keypad(
                onDigit = { d -> (cents * 10 + d).takeIf { it <= MAX_CENTS }?.let { cents = it } },
                onBackspace = { cents /= 10 },
                modifier = Modifier.weight(1f),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodButton("Pix (QR Code)", Icons.Filled.QrCode2, cents > 0) { dialog = Payment.PIX }
            MethodButton("Aproximação (NFC)", Icons.Filled.Contactless, cents > 0) { dialog = Payment.NFC }
            MethodButton("Dinheiro", Icons.Filled.Payments, cents > 0) { dialog = Payment.CASH }
            Spacer(Modifier.height(8.dp))
            Text("Recebido hoje: ${formatBrl(payments.sumOf { it.amountCents })}", style = MaterialTheme.typography.titleMedium)
            val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            LazyColumn(Modifier.weight(1f)) {
                items(payments, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(time.format(Date(p.time)), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                        Text(p.method, Modifier.weight(1f))
                        Text(formatBrl(p.amountCents), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Icon(icon, null, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Keypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit, modifier: Modifier) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, -1))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (key == null) Modifier
                                else Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { if (key == -1) onBackspace() else onDigit(key) }
                            ),
                    ) {
                        when (key) {
                            null -> Unit
                            -1 -> Icon(Icons.AutoMirrored.Filled.Backspace, "Apagar")
                            else -> Text("$key", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixDialog(
    cents: Long, key: String, name: String, city: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit, onOpenSettings: () -> Unit,
) {
    if (key.isBlank()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cadastre sua chave Pix") },
            text = { Text("Para gerar o QR Code, informe sua chave Pix em Ajustes.") },
            confirmButton = { Button(onClick = onOpenSettings) { Text("Abrir Ajustes") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )
        return
    }
    val context = LocalContext.current
    val payload = remember(cents, key, name, city) { PixPayload.build(key, name, city, cents) }
    val qr = remember(payload) { qrBitmap(payload).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pix · ${formatBrl(cents)}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(qr, "QR Code do Pix", Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
                Text("Peça ao passageiro para ler o código no app do banco. Confira o recebimento no seu banco antes de confirmar.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { copy(context, payload) }) { Text("Copiar Pix Copia e Cola") }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Recebi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NfcDialog(
    cents: Long, acquirerPkg: String,
    onDismiss: () -> Unit, onConfirm: () -> Unit, onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val acquirerInstalled = acquirerPkg.isNotEmpty() && context.packageManager.getLaunchIntentForPackage(acquirerPkg) != null
    var opened by rememberSaveable { mutableStateOf(false) }

    val (message, action) = when {
        adapter == null -> "Este aparelho não tem NFC, então não dá para receber por aproximação nele. Use o Pix." to null
        !adapter.isEnabled -> "O NFC está desligado. Ligue-o nas configurações do tablet." to
            ("Abrir configurações" to { Apps.start(context, Intent(Settings.ACTION_NFC_SETTINGS)); Unit })
        !acquirerInstalled -> "Escolha em Ajustes o app da sua adquirente (Tap to Pay) para receber por aproximação." to
            ("Abrir Ajustes" to onOpenSettings)
        !opened -> "O valor será copiado. O app de pagamento abre para o passageiro aproximar o cartão ou celular." to
            ("Abrir app de pagamento" to {
                copy(context, "%d,%02d".format(cents / 100, cents % 100))
                opened = Apps.launch(context, acquirerPkg)
            })
        else -> "Pagamento aprovado no app de pagamento?" to ("Recebi" to onConfirm)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aproximação · ${formatBrl(cents)}") },
        text = { Text(message) },
        confirmButton = { action?.let { (label, run) -> Button(onClick = run) { Text(label) } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Volts Driver", text))
}
