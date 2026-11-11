package br.com.volts.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.volts.driver.apps.AppEntry

/** Lista de apps instalados; [trailing] permite colocar controles (ex.: caixas de seleção) em cada linha. */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppEntry>,
    onDismiss: () -> Unit,
    trailing: @Composable (AppEntry) -> Unit = {},
    onPick: (AppEntry) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(apps, key = { it.pkg }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(entry) }.padding(vertical = 8.dp),
                    ) {
                        Image(entry.icon, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(entry.label, Modifier.weight(1f))
                        trailing(entry)
                    }
                }
            }
        },
    )
}
