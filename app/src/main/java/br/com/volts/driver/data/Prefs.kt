package br.com.volts.driver.data

import android.content.Context
import br.com.volts.driver.apps.KnownApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val pixKey: String = "",
    val pixName: String = "",
    val pixCity: String = "",
    /** App da adquirente usado para receber por aproximação (Tap to Pay). */
    val acquirerPkg: String = "",
    /** Apps fixados no painel. */
    val pinned: Set<String> = KnownApps.all.map { it.pkg }.toSet(),
    /** Apps de corrida cujas ofertas entram no relatório. */
    val monitored: Set<String> = KnownApps.all.filter { it.ride }.map { it.pkg }.toSet(),
    val radiusKm: Int = 5,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("volts", Context.MODE_PRIVATE)
    private val defaults = Settings()

    private val _settings = MutableStateFlow(
        Settings(
            pixKey = sp.getString("pixKey", "")!!,
            pixName = sp.getString("pixName", "")!!,
            pixCity = sp.getString("pixCity", "")!!,
            acquirerPkg = sp.getString("acquirerPkg", "")!!,
            pinned = sp.getStringSet("pinned", null)?.toSet() ?: defaults.pinned,
            monitored = sp.getStringSet("monitored", null)?.toSet() ?: defaults.monitored,
            radiusKm = sp.getInt("radiusKm", defaults.radiusKm),
        )
    )
    val settings: StateFlow<Settings> = _settings

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("pixKey", s.pixKey)
            .putString("pixName", s.pixName)
            .putString("pixCity", s.pixCity)
            .putString("acquirerPkg", s.acquirerPkg)
            .putStringSet("pinned", s.pinned)
            .putStringSet("monitored", s.monitored)
            .putInt("radiusKm", s.radiusKm)
            .apply()
    }
}
