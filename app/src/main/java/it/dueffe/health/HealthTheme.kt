package it.dueffe.health

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal data class HealthPalette(
    val id: String,
    val label: String,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val background: Color,
    val surfaceVariant: Color,
    val outline: Color
)

internal val healthPalettes = listOf(
    HealthPalette("material", "Material", Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF7D5260), Color(0xFFFFD8E4), Color(0xFFFFFBFE), Color(0xFFE7E0EC), Color(0xFF79747E)),
    HealthPalette("blue", "Blu", Color(0xFF415F91), Color.White, Color(0xFFD6E3FF), Color(0xFF001B3E), Color(0xFF565F71), Color(0xFFDAE2F9), Color(0xFF705575), Color(0xFFFAD8FD), Color(0xFFF9F9FF), Color(0xFFE1E2EC), Color(0xFF74777F)),
    HealthPalette("green", "Verde", Color(0xFF386A20), Color.White, Color(0xFFB8F397), Color(0xFF062100), Color(0xFF55624C), Color(0xFFD9E7CB), Color(0xFF386666), Color(0xFFBCEBEC), Color(0xFFFDFDF5), Color(0xFFE0E4D9), Color(0xFF74796F)),
    HealthPalette("teal", "Turchese", Color(0xFF006A6A), Color.White, Color(0xFF9CF1F0), Color(0xFF002020), Color(0xFF4A6363), Color(0xFFCCE8E7), Color(0xFF4B607C), Color(0xFFD3E4FF), Color(0xFFFAFDFC), Color(0xFFDAE5E4), Color(0xFF6F7979))
)

internal fun healthPalette(id: String): HealthPalette =
    healthPalettes.firstOrNull { it.id == id } ?: healthPalettes.first()

internal fun healthColorScheme(id: String): ColorScheme {
    val p = healthPalette(id)
    return lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.secondary,
        secondaryContainer = p.secondaryContainer,
        tertiary = p.tertiary,
        tertiaryContainer = p.tertiaryContainer,
        background = p.background,
        surface = p.background,
        surfaceVariant = p.surfaceVariant,
        outline = p.outline
    )
}
