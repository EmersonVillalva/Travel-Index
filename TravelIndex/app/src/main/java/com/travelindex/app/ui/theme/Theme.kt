package com.travelindex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta TravelIndex
val IndexNavy = Color(0xFF0F1E36)
val RouteTeal = Color(0xFF1FB5A8)
val SunriseAmber = Color(0xFFF5A524)

private val Light = lightColorScheme(
    primary = IndexNavy, onPrimary = Color.White,
    secondary = Color(0xFF0E8077), onSecondary = Color.White,
    tertiary = Color(0xFFB36B00), onTertiary = Color.White,
    background = Color(0xFFF4F7FA), onBackground = Color(0xFF0F1E36),
    surface = Color.White, onSurface = Color(0xFF0F1E36),
    surfaceVariant = Color(0xFFE6ECF2), onSurfaceVariant = Color(0xFF45546A),
    error = Color(0xFFBA1A1A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FB4FF), onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF3CD3C5), onSecondary = Color(0xFF00201D),
    tertiary = Color(0xFFFFB84D), onTertiary = Color(0xFF2B1800),
    background = Color(0xFF0B1220), onBackground = Color(0xFFE8EEF7),
    surface = Color(0xFF131C2E), onSurface = Color(0xFFE8EEF7),
    surfaceVariant = Color(0xFF1E2A40), onSurfaceVariant = Color(0xFFA9B6CA),
    error = Color(0xFFFFB4AB),
)

// Fuente del sistema hasta empaquetar Plus Jakarta Sans / Inter en res/font (ver docs/DESIGN.md).
private val Brand = FontFamily.SansSerif
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Brand, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Brand, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

object Spacing { val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp }

@Composable
fun TravelIndexTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography, shapes = AppShapes, content = content,
    )
}

/** Colores semánticos de estado (no dependen del tema claro/oscuro). */
val StatusOk = Color(0xFF1FB5A8)
val StatusWarn = Color(0xFFF5A524)
val StatusDanger = Color(0xFFE5534B)
val StatusNeutral = Color(0xFF8A97AB)
