package com.p2pmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Brand = Color(0xFF3D5AFE)
private val BrandDark = Color(0xFFB6C2FF)
private val Success = Color(0xFF1E8E3E)
private val Danger = Color(0xFFD93025)

val ColorScheme_Success @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6DD58C) else Success
val ColorScheme_Danger @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Danger

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE3FF),
    onPrimaryContainer = Color(0xFF00114F),
    secondary = Color(0xFF5B5D72),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFBFAFF),
    surfaceVariant = Color(0xFFE3E1EC),
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF001259),
    primaryContainer = Color(0xFF25348A),
    onPrimaryContainer = Color(0xFFDEE3FF),
    secondary = Color(0xFFC4C5DD),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF45464F),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun P2PMessengerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
