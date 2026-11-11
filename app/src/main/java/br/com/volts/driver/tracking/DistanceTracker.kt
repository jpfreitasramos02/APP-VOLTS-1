package br.com.volts.driver.tracking

import br.com.volts.driver.util.Geo
import kotlin.math.max

/**
 * Soma a distância percorrida a partir das posições do GPS, descartando leituras
 * imprecisas, o "tremor" do aparelho parado e saltos impossíveis.
 */
class DistanceTracker(
    private val maxAccuracyM: Float = 35f,
    private val minStepM: Double = 8.0,
    private val maxSpeedMs: Double = 70.0, // ~250 km/h
) {
    private class Fix(val lat: Double, val lon: Double, val timeMs: Long)

    private var last: Fix? = null

    /** Retorna quantos metros esta leitura acrescenta ao total. */
    fun onFix(lat: Double, lon: Double, accuracyM: Float, timeMs: Long): Double {
        if (accuracyM > maxAccuracyM) return 0.0
        val fix = Fix(lat, lon, timeMs)
        val prev = last
        if (prev == null) {
            last = fix
            return 0.0
        }
        val d = Geo.distanceMeters(prev.lat, prev.lon, lat, lon)
        // Parado: mantém o ponto anterior para não acumular ruído.
        if (d < max(minStepM, accuracyM * 0.5)) return 0.0
        val dt = (timeMs - prev.timeMs) / 1000.0
        last = fix
        if (dt <= 0 || d / dt > maxSpeedMs) return 0.0
        return d
    }
}
