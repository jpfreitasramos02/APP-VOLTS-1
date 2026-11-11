package br.com.volts.driver.offers

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.volts.driver.VoltsApp
import br.com.volts.driver.data.Offer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lê o texto da tela dos apps de corrida monitorados e registra cada oferta
 * (valor, km, minutos) para o relatório. Nenhum texto bruto é armazenado.
 */
class OfferAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastSeen = HashMap<String, Pair<ParsedOffer, Long>>()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val app = application as VoltsApp
        if (pkg !in app.prefs.settings.value.monitored) return
        val root = rootInActiveWindow ?: event.source ?: return
        if (root.packageName?.toString() != pkg) return

        val text = StringBuilder()
        collect(root, text, intArrayOf(0))
        val offer = OfferParser.parse(text.toString()) ?: return

        // O mesmo cartão gera vários eventos enquanto está na tela.
        val now = System.currentTimeMillis()
        val last = lastSeen[pkg]
        if (last != null && last.first == offer && now - last.second < DEDUPE_WINDOW_MS) {
            lastSeen[pkg] = offer to now
            return
        }
        lastSeen[pkg] = offer to now
        scope.launch {
            app.dao.insertOffer(Offer(time = now, pkg = pkg, valueCents = offer.valueCents, km = offer.km, minutes = offer.minutes))
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: StringBuilder, count: IntArray) {
        if (count[0]++ > MAX_NODES) return
        (node.text ?: node.contentDescription)?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out, count) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_NODES = 400
        const val DEDUPE_WINDOW_MS = 90_000L
    }
}
