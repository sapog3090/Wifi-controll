package io.github.sapog3090.opprice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Цвета, которых нет в Material: «цена упала» и тонкие разделители. */
@Immutable
data class ExtraColors(val drop: Color, val rise: Color, val hairline: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color.Green, Color.Red, Color.Gray) }

private val Accent = Color(0xFFD7262E)
private val AccentDark = Color(0xFFFF5A5F)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color(0xFFF3F1EC),
    onBackground = Color(0xFF16161A),
    surface = Color(0xFFF3F1EC),
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFE6E3DC),
    onSurfaceVariant = Color(0xFF625F59),
    surfaceContainer = Color(0xFFEAE7E0),
    secondaryContainer = Color(0xFFDCD8CF),
    onSecondaryContainer = Color(0xFF16161A),
    outline = Color(0xFFBDB8AE),
    outlineVariant = Color(0xFFD9D5CC),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF16161A),
    background = Color(0xFF101012),
    onBackground = Color(0xFFECEAE6),
    surface = Color(0xFF101012),
    onSurface = Color(0xFFECEAE6),
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = Color(0xFF9C9A95),
    surfaceContainer = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2C2C31),
    onSecondaryContainer = Color(0xFFECEAE6),
    outline = Color(0xFF4A4A50),
    outlineVariant = Color(0xFF2A2A2F),
    error = Color(0xFFFF8A80),
)

private val LightExtra = ExtraColors(drop = Color(0xFF1B7A46), rise = Color(0xFF9A3B1B), hairline = Color(0xFFDDD9D0))
private val DarkExtra = ExtraColors(drop = Color(0xFF52C48C), rise = Color(0xFFE5866A), hairline = Color(0xFF26262B))

/** Цены: табличные цифры, чтобы столбец не «прыгал». */
val PriceStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-0.2).sp)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun OpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
