package com.afmc.find.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0C0E)
val Surface = Color(0xFF141619)
val SurfaceHigh = Color(0xFF1C1F24)
val Stroke = Color(0xFF2A2E36)
val Accent = Color(0xFF7C9CFF)
val AccentSoft = Color(0xFFB9C8FF)
val TextPrimary = Color(0xFFECEEF2)
val TextMuted = Color(0xFF8A909A)
val TextDim = Color(0xFF5E646F)
val Danger = Color(0xFFF87171)
val Success = Color(0xFF6EE7B7)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B0C0E),
    secondary = AccentSoft,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    error = Danger,
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 17.sp, color = TextPrimary, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, color = TextMuted, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextMuted),
)

@Composable
fun AfmcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
