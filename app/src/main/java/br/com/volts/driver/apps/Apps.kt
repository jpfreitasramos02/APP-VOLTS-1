package br.com.volts.driver.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KnownApp(val pkg: String, val label: String, val ride: Boolean)

/** Apps que já vêm fixados no painel. O motorista pode fixar qualquer outro em Ajustes. */
object KnownApps {
    val all = listOf(
        KnownApp("com.ubercab.driver", "Uber Driver", ride = true),
        KnownApp("com.app99.driver", "99 Motorista", ride = true),
        KnownApp("sinet.startup.inDriver", "inDrive", ride = true),
        KnownApp("com.waze", "Waze", ride = false),
        KnownApp("com.google.android.apps.maps", "Google Maps", ride = false),
    )
    private val byPkg = all.associateBy { it.pkg }
    fun label(pkg: String): String? = byPkg[pkg]?.label
    fun order(pkg: String): Int = all.indexOfFirst { it.pkg == pkg }.let { if (it < 0) Int.MAX_VALUE else it }
}

class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

object Apps {
    fun loadInstalled(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun launchIntent(context: Context, pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre o app; com [sideBySide], pede ao Android para abri-lo em tela dividida ao lado do painel. */
    fun launch(context: Context, pkg: String, sideBySide: Boolean = false): Boolean {
        val intent = launchIntent(context, pkg) ?: return false
        if (sideBySide) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return start(context, intent)
    }

    /** Abre vários apps em sequência (ficam todos ativos; o último fica na frente). */
    fun launchAll(context: Context, pkgs: List<String>) {
        val intents = pkgs.mapNotNull { launchIntent(context, it) }
        if (intents.isNotEmpty()) context.startActivities(intents.toTypedArray())
    }

    fun openStore(context: Context, pkg: String) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) ||
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }

    /** Abre a navegação até o ponto no app de mapas que o motorista escolher (Waze, Maps...). */
    fun navigateTo(context: Context, lat: Double, lon: Double, label: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")))

    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Lista de apps instalados, recarregada sempre que a tela volta ao primeiro plano. */
@Composable
fun rememberInstalledApps(): State<List<AppEntry>?> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf<List<AppEntry>?>(null) }
    LifecycleResumeEffect(Unit) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { state.value = Apps.loadInstalled(context) }
        onPauseOrDispose { scope.cancel() }
    }
    return state
}
