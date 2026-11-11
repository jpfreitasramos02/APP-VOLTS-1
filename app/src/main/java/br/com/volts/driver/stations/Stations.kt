package br.com.volts.driver.stations

import br.com.volts.driver.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class StationType { FUEL, EV }

data class Station(
    val id: Long,
    val type: StationType,
    val name: String,
    val details: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Double,
    /** Eletroposto da rede Volts (destacado no mapa e no topo da lista). */
    val isVolts: Boolean,
)

/** Postos de combustível e eletropostos próximos, a partir do OpenStreetMap (Overpass API). */
object Stations {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    suspend fun fetchNearby(lat: Double, lon: Double, radiusM: Int): List<Station> = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:20];
            (
              nwr["amenity"="fuel"](around:$radiusM,$lat,$lon);
              nwr["amenity"="charging_station"](around:$radiusM,$lat,$lon);
            );
            out center tags 300;
        """.trimIndent()
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "VoltsDriver/0.1 (Android)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Servidor de mapas respondeu ${conn.responseCode}")
            parse(conn.inputStream.bufferedReader().use { it.readText() }, lat, lon)
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String, originLat: Double, originLon: Double): List<Station> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Station>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val center = el.optJSONObject("center")
            val lat = if (el.has("lat")) el.getDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (el.has("lon")) el.getDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lon.isNaN()) continue
            val type = when (tags.optString("amenity")) {
                "fuel" -> StationType.FUEL
                "charging_station" -> StationType.EV
                else -> continue
            }
            fun tag(k: String) = tags.optString(k).takeIf { it.isNotBlank() }
            val brandInfo = listOfNotNull(tag("name"), tag("brand"), tag("operator"), tag("network"))
            val name = brandInfo.firstOrNull()
                ?: if (type == StationType.FUEL) "Posto de combustível" else "Eletroposto"
            val details = buildList {
                brandInfo.drop(1).firstOrNull { !it.equals(name, ignoreCase = true) }?.let { add(it) }
                if (type == StationType.EV) {
                    tag("capacity")?.let { add("$it vagas") }
                    val plugs = SOCKETS.filter { (k, _) -> tags.has("socket:$k") }.map { it.second }
                    if (plugs.isNotEmpty()) add(plugs.joinToString("/"))
                }
                if (tag("opening_hours") == "24/7") add("24h")
            }.joinToString(" · ")
            out += Station(
                id = el.optLong("id"),
                type = type,
                name = name,
                details = details,
                lat = lat,
                lon = lon,
                distanceM = Geo.distanceMeters(originLat, originLon, lat, lon),
                isVolts = type == StationType.EV && brandInfo.any { it.contains("volts", ignoreCase = true) },
            )
        }
        return out.sortedWith(compareByDescending<Station> { it.isVolts }.thenBy { it.distanceM })
    }

    private val SOCKETS = listOf(
        "type2" to "Tipo 2",
        "type2_combo" to "CCS2",
        "chademo" to "CHAdeMO",
        "gb_dc" to "GB/T",
        "type1" to "Tipo 1",
    )
}
