package it.dueffe.health

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Ogni palette definisce i tre accenti delle aree: terapie (primary), visite (secondary), documenti (tertiary). */
internal data class HealthPalette(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color
)

internal const val DEFAULT_PALETTE = "health"

internal val healthPalettes = listOf(
    HealthPalette(DEFAULT_PALETTE, "Aurora", Color(0xFF4F46E5), Color(0xFF0E9488), Color(0xFFE2603F), Color(0xFFF5F6FB)),
    HealthPalette("material", "Lavanda", Color(0xFF6750A4), Color(0xFF2B8A8C), Color(0xFFD25E7A), Color(0xFFF8F6FC)),
    HealthPalette("blue", "Oceano", Color(0xFF1F5FD1), Color(0xFF0889A8), Color(0xFFE07A2C), Color(0xFFF3F7FC)),
    HealthPalette("green", "Salvia", Color(0xFF2E7D4B), Color(0xFF0F7F92), Color(0xFFD27B2A), Color(0xFFF4F8F3)),
    HealthPalette("teal", "Laguna", Color(0xFF00747A), Color(0xFF4565D8), Color(0xFFE0675A), Color(0xFFF2F8F8)),
    HealthPalette("rose", "Corallo", Color(0xFFCB3F68), Color(0xFF6C4BD6), Color(0xFF0E8F8A), Color(0xFFFCF5F7))
)

internal fun healthPalette(id: String): HealthPalette =
    healthPalettes.firstOrNull { it.id == id } ?: healthPalettes.first()

internal fun healthColorScheme(id: String): ColorScheme {
    val p = healthPalette(id)
    fun light(c: Color, amount: Float) = lerp(Color.White, c, amount)
    fun deep(c: Color) = lerp(c, Color.Black, 0.5f)
    val ink = lerp(Color(0xFF12141C), p.primary, 0.08f)
    return lightColorScheme(
        primary = p.primary, onPrimary = Color.White,
        primaryContainer = light(p.primary, 0.14f), onPrimaryContainer = deep(p.primary),
        inversePrimary = light(p.primary, 0.6f),
        secondary = p.secondary, onSecondary = Color.White,
        secondaryContainer = light(p.secondary, 0.14f), onSecondaryContainer = deep(p.secondary),
        tertiary = p.tertiary, onTertiary = Color.White,
        tertiaryContainer = light(p.tertiary, 0.15f), onTertiaryContainer = deep(p.tertiary),
        background = p.background, onBackground = ink,
        surface = p.background, onSurface = ink,
        surfaceVariant = light(p.primary, 0.08f), onSurfaceVariant = lerp(Color(0xFF5A5F70), p.primary, 0.1f),
        surfaceTint = p.primary,
        surfaceBright = Color.White, surfaceDim = light(p.primary, 0.12f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = light(p.primary, 0.035f),
        surfaceContainer = light(p.primary, 0.06f),
        surfaceContainerHigh = light(p.primary, 0.09f),
        surfaceContainerHighest = light(p.primary, 0.12f),
        outline = lerp(Color(0xFF8B90A0), p.primary, 0.1f),
        outlineVariant = lerp(Color(0xFFE3E5EC), p.primary, 0.05f),
        error = Color(0xFFD03248), onError = Color.White,
        errorContainer = Color(0xFFFFE3E7), onErrorContainer = Color(0xFF7A1022)
    )
}

private val healthShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
private fun themeColor(target: Color): Color = animateColorAsState(target, tween(650), label = "Colore tema").value

/** Il cambio di palette sfuma i colori invece di saltare da uno schema all'altro. */
@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = themeColor(primary), onPrimary = themeColor(onPrimary),
    primaryContainer = themeColor(primaryContainer), onPrimaryContainer = themeColor(onPrimaryContainer),
    secondary = themeColor(secondary), secondaryContainer = themeColor(secondaryContainer), onSecondaryContainer = themeColor(onSecondaryContainer),
    tertiary = themeColor(tertiary), tertiaryContainer = themeColor(tertiaryContainer), onTertiaryContainer = themeColor(onTertiaryContainer),
    background = themeColor(background), surface = themeColor(surface),
    onSurfaceVariant = themeColor(onSurfaceVariant),
    surfaceContainerLow = themeColor(surfaceContainerLow), surfaceContainer = themeColor(surfaceContainer),
    surfaceContainerHigh = themeColor(surfaceContainerHigh), outlineVariant = themeColor(outlineVariant)
)

@Composable
internal fun HealthTheme(paletteId: String, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context)
    else healthColorScheme(paletteId)
    MaterialTheme(colorScheme = scheme.animated(), shapes = healthShapes, content = content)
}
