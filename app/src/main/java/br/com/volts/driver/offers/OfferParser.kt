package br.com.volts.driver.offers

/** Números extraídos de um cartão de oferta de corrida. */
data class ParsedOffer(val valueCents: Long, val km: Double, val minutes: Int) {
    val brlPerKm: Double? get() = if (km > 0) valueCents / 100.0 / km else null
}

/**
 * Heurística genérica para reconhecer uma oferta de corrida no texto da tela:
 * precisa ter um valor em reais, pelo menos uma distância em km e um tempo em min.
 * Distâncias e tempos são somados (deslocamento até o passageiro + viagem).
 */
object OfferParser {
    private val money = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?(\s*/\s*(?:km|h|hora|min))?""", RegexOption.IGNORE_CASE)
    private val km = Regex("""(?<![\d/,.])(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val min = Regex("""(?<![\d/,.])(\d+)\s*min""", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedOffer? {
        val value = money.findAll(text)
            .filter { it.groupValues[3].isEmpty() } // ignora tarifas "R$ 1,80/km"
            .map { m ->
                val reais = m.groupValues[1].replace(".", "").toLong()
                val cents = m.groupValues[2].padEnd(2, '0').toLong()
                reais * 100 + cents
            }
            .firstOrNull { it in 200..200_000 } ?: return null

        val totalKm = km.findAll(text).sumOf { it.groupValues[1].replace(',', '.').toDouble() }
        val totalMin = min.findAll(text).sumOf { it.groupValues[1].toInt() }
        if (totalKm <= 0.0 || totalKm > 500 || totalMin <= 0) return null
        return ParsedOffer(value, totalKm, totalMin)
    }
}
