package br.com.volts.driver.util

import java.util.Locale

private val PT_BR = Locale("pt", "BR")

fun formatBrl(cents: Long): String =
    "R$ " + String.format(PT_BR, "%,.2f", cents / 100.0)

fun formatKm(meters: Double): String =
    String.format(PT_BR, "%.1f km", meters / 1000.0)

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else formatKm(meters)

fun formatDuration(millis: Long): String {
    val totalMin = (millis / 60_000).coerceAtLeast(0)
    return "%dh %02dmin".format(totalMin / 60, totalMin % 60)
}

fun formatDecimal(value: Double, digits: Int = 2): String =
    String.format(PT_BR, "%.${digits}f", value)
