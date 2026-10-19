package com.blacksingularities.openexplorer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.blacksingularities.openexplorer.data.Settings

val ACCENTS = listOf(
    0xFF3D7BF7, 0xFF7C5CFF, 0xFF00A884, 0xFFE8590C, 0xFFE03131, 0xFFD6336C, 0xFF0CA678, 0xFF495057,
)

@Composable
fun isDark(settings: Settings): Boolean = when (settings.theme) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

private fun seeded(accent: Color, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = lerp(accent, Color.White, 0.25f),
            onPrimary = Color(0xFF0B1220),
            primaryContainer = lerp(accent, Color(0xFF101318), 0.65f),
            onPrimaryContainer = lerp(accent, Color.White, 0.8f),
            secondaryContainer = lerp(accent, Color(0xFF1A1E25), 0.8f),
            background = Color(0xFF101318),
            surface = Color(0xFF101318),
            surfaceContainerLowest = Color(0xFF0C0E12),
            surfaceContainerLow = Color(0xFF15181E),
            surfaceContainer = Color(0xFF1A1E25),
            surfaceContainerHigh = Color(0xFF22262E),
            surfaceContainerHighest = Color(0xFF2B3039),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.82f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.55f),
            secondaryContainer = lerp(accent, Color.White, 0.88f),
            background = Color(0xFFF8F9FC),
            surface = Color(0xFFF8F9FC),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF3F5F9),
            surfaceContainer = Color(0xFFEDF0F5),
            surfaceContainerHigh = Color(0xFFE7EAF0),
            surfaceContainerHighest = Color(0xFFE0E4EB),
        )
    }
}

@Composable
fun OpenExplorerTheme(settings: Settings, incognito: Boolean = false, content: @Composable () -> Unit) {
    val dark = isDark(settings) || incognito
    val ctx = LocalContext.current
    var scheme = when {
        incognito -> seeded(Color(0xFF8E7CFF), true).copy(
            background = Color(0xFF16121F), surface = Color(0xFF16121F),
            surfaceContainer = Color(0xFF221C2E), surfaceContainerHigh = Color(0xFF2A2338),
            surfaceContainerHighest = Color(0xFF332A44), surfaceContainerLow = Color(0xFF1B1626),
        )
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> seeded(Color(settings.accent), dark)
    }
    if (dark && settings.amoled && !incognito) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
