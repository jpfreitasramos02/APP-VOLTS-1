package br.com.volts.driver.pay

import java.text.Normalizer

/**
 * Gera o "Pix Copia e Cola" (BR Code estático, padrão EMV do Banco Central)
 * para a chave do motorista com o valor da corrida.
 */
object PixPayload {

    fun build(key: String, merchantName: String, merchantCity: String, amountCents: Long, txId: String = "***"): String {
        val account = tlv("00", "br.gov.bcb.pix") + tlv("01", key.trim())
        val body = buildString {
            append(tlv("00", "01"))
            append(tlv("26", account))
            append(tlv("52", "0000"))
            append(tlv("53", "986"))
            if (amountCents > 0) append(tlv("54", "%d.%02d".format(amountCents / 100, amountCents % 100)))
            append(tlv("58", "BR"))
            append(tlv("59", sanitize(merchantName, 25).ifEmpty { "MOTORISTA" }))
            append(tlv("60", sanitize(merchantCity, 15).ifEmpty { "BRASIL" }))
            append(tlv("62", tlv("05", txId)))
            append("6304")
        }
        return body + "%04X".format(crc16(body))
    }

    private fun tlv(id: String, value: String) = id + "%02d".format(value.length) + value

    /** Remove acentos e caracteres fora do conjunto aceito pelos bancos. */
    private fun sanitize(s: String, max: Int): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .uppercase()
            .take(max)
            .trim()

    /** CRC16-CCITT (polinômio 0x1021, valor inicial 0xFFFF). */
    fun crc16(data: String): Int {
        var crc = 0xFFFF
        for (b in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
