package com.afmc.find.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF15171B)
val Surface = Color(0xFF1B1D22)
val SurfaceHigh = Color(0xFF22262C)
val Stroke = Color(0xFF2E323A)
val Accent = Color(0xFFF43F70)
val AccentSoft = Color(0xFFFFA3B5)
val TextPrimary = Color(0xFFF4F2F0)
val TextMuted = Color(0xFFB8BDC7)
val TextDim = Color(0xFF7C828C)
val Danger = Color(0xFFF87171)
val Success = Color(0xFFADD8BE)

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
