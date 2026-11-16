package br.com.volts.driver.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.volts.driver.R

// Identidade visual da Volts Brasil (mesmos valores do site).
val VoltsAmarelo = Color(0xFFFFC905)
val VoltsAzul = Color(0xFF0A84D8)
val VoltsVerde = Color(0xFF00D26C)
val VoltsBlack = Color(0xFF07080A)
val VoltsGraphite = Color(0xFF0F1115)
val VoltsGraphite2 = Color(0xFF16191F)
val VoltsLine = Color(0xFF262A32)
val VoltsMuted = Color(0xFF9198A5)
val VoltsText = Color(0xFFEDEFF2)

/** Postos de combustível no mapa (fora da paleta da marca, para não confundir com a rede Volts). */
val FuelOrange = Color(0xFFFF7A45)

private val Colors = darkColorScheme(
    primary = VoltsAmarelo,
    onPrimary = VoltsBlack,
    primaryContainer = Color(0xFF2B2305),
    onPrimaryContainer = VoltsAmarelo,
    secondary = VoltsAzul,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B2305),
    onSecondaryContainer = VoltsAmarelo,
    tertiary = VoltsVerde,
    onTertiary = VoltsBlack,
    background = VoltsBlack,
    onBackground = VoltsText,
    surface = VoltsBlack,
    onSurface = VoltsText,
    surfaceVariant = VoltsGraphite2,
    onSurfaceVariant = VoltsMuted,
    surfaceContainerLowest = VoltsBlack,
    surfaceContainerLow = VoltsGraphite,
    surfaceContainer = VoltsGraphite,
    surfaceContainerHigh = VoltsGraphite2,
    surfaceContainerHighest = VoltsLine,
    outline = Color(0xFF4A505C),
    outlineVariant = VoltsLine,
)

@OptIn(ExperimentalTextApi::class)
private val Sora = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.sora, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

/** Fonte "técnica" do site, usada em rótulos pequenos. */
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

val TechLabel = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp)

private val VoltsTypography = Typography().run {
    fun TextStyle.sora() = copy(fontFamily = Sora)
    copy(
        displayLarge = displayLarge.sora(), displayMedium = displayMedium.sora(), displaySmall = displaySmall.sora(),
        headlineLarge = headlineLarge.sora(), headlineMedium = headlineMedium.sora(), headlineSmall = headlineSmall.sora(),
        titleLarge = titleLarge.sora(), titleMedium = titleMedium.sora(), titleSmall = titleSmall.sora(),
        bodyLarge = bodyLarge.sora(), bodyMedium = bodyMedium.sora(), bodySmall = bodySmall.sora(),
        labelLarge = labelLarge.sora(), labelMedium = labelMedium.sora(), labelSmall = labelSmall.sora(),
    )
}

@Composable
fun VoltsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = VoltsTypography, content = content)
}
