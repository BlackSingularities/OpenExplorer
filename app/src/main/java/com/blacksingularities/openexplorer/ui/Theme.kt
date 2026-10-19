package com.blacksingularities.openexplorer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openexplorer.data.Settings

val ACCENTS = listOf(
    0xFF3D7BF7, 0xFF7C5CFF, 0xFF00A884, 0xFFE8590C, 0xFFE03131, 0xFFD6336C, 0xFF0CA678, 0xFF495057,
)

/** Wymiary interfejsu zależne od gęstości (kompaktowy / zwykły). */
class Dims(val bar: Dp, val field: Dp, val icon: Dp, val button: Dp)

val CompactDims = Dims(bar = 50.dp, field = 38.dp, icon = 20.dp, button = 40.dp)
val NormalDims = Dims(bar = 60.dp, field = 46.dp, icon = 22.dp, button = 46.dp)

val LocalDims = staticCompositionLocalOf { CompactDims }

@Composable
fun isDark(settings: Settings): Boolean = when (settings.theme) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

/** Czarno-biała paleta jak w OpenPDF: akcent = biel (w jasnym motywie – czerń). */
private fun mono(dark: Boolean, amoled: Boolean): ColorScheme = if (dark) {
    val bg = if (amoled) Color.Black else Color(0xFF0E0E0F)
    darkColorScheme(
        primary = Color.White, onPrimary = Color.Black,
        primaryContainer = Color(0xFF2A2A2C), onPrimaryContainer = Color.White,
        secondary = Color(0xFFE0E0E0), onSecondary = Color.Black,
        secondaryContainer = Color(0xFF242426), onSecondaryContainer = Color.White,
        background = bg, onBackground = Color.White,
        surface = bg, onSurface = Color.White,
        surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xB3FFFFFF),
        surfaceContainerLowest = bg, surfaceContainerLow = if (amoled) Color(0xFF080808) else Color(0xFF131314),
        surfaceContainer = Color(0xFF161618), surfaceContainerHigh = Color(0xFF1C1C1E), surfaceContainerHighest = Color(0xFF262628),
        outline = Color(0x33FFFFFF), outlineVariant = Color(0x1FFFFFFF),
        error = Color(0xFFFF3B47),
    )
} else {
    lightColorScheme(
        primary = Color.Black, onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E6E8), onPrimaryContainer = Color.Black,
        secondary = Color(0xFF333333), onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDEDEF), onSecondaryContainer = Color.Black,
        background = Color.White, onBackground = Color.Black,
        surface = Color.White, onSurface = Color.Black,
        surfaceVariant = Color(0xFFF2F2F4), onSurfaceVariant = Color(0x99000000),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8FA),
        surfaceContainer = Color(0xFFF2F2F4), surfaceContainerHigh = Color(0xFFEDEDF0), surfaceContainerHighest = Color(0xFFE5E5E8),
        outline = Color(0x26000000), outlineVariant = Color(0x14000000),
        error = Color(0xFFE5202E),
    )
}

private fun seeded(accent: Color, dark: Boolean): ColorScheme = if (dark) {
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

private val typography = Typography().let { t ->
    t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp),
        titleMedium = t.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontSize = 13.sp),
    )
}

@Composable
fun OpenExplorerTheme(settings: Settings, incognito: Boolean = false, content: @Composable () -> Unit) {
    val dark = isDark(settings) || incognito
    val ctx = LocalContext.current
    var scheme = when {
        incognito -> mono(true, true).copy(
            background = Color(0xFF120F18), surface = Color(0xFF120F18),
            surfaceContainerLow = Color(0xFF17131F), surfaceContainer = Color(0xFF1C1725),
            surfaceContainerHigh = Color(0xFF241E2F), surfaceContainerHighest = Color(0xFF2D2639),
            primary = Color(0xFFD9CCFF),
        )
        settings.palette == "dynamic" && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        settings.palette == "accent" -> seeded(Color(settings.accent), dark)
        else -> mono(dark, settings.amoled)
    }
    if (dark && settings.amoled && !incognito && settings.palette != "mono") {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}
