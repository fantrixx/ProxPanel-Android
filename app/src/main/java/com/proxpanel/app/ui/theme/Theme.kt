package com.proxpanel.app.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ProxPanel brand — Proxmox orange on deep slate (one cohesive dark product shell).
val ProxBg = Color(0xFF0A0C10)
val ProxSurface = Color(0xFF12161E)
val ProxSurface2 = Color(0xFF1A2030)
val ProxSurface3 = Color(0xFF222A3A)
val ProxLine = Color(0xFF2E3748)
val ProxInk = Color(0xFFF2F5FA)
val ProxMuted = Color(0xFF94A0B4)
val ProxAccent = Color(0xFFFF7A1A)
val ProxAccent2 = Color(0xFFFFA05C)
val ProxGood = Color(0xFF3DDC97)
val ProxWarn = Color(0xFFF5C542)
val ProxBad = Color(0xFFFF6B6B)
val ProxVm = Color(0xFF5B8CFF)
val ProxVmContainer = Color(0xFF1A2748)
val ProxCt = Color(0xFF2EC4A4)
val ProxCtContainer = Color(0xFF12352E)

private val DarkColors = darkColorScheme(
    primary = ProxAccent,
    onPrimary = Color(0xFF1A0C00),
    primaryContainer = Color(0xFF3D2208),
    onPrimaryContainer = ProxAccent2,
    secondary = ProxAccent2,
    onSecondary = Color(0xFF1A0C00),
    secondaryContainer = Color(0xFF2A2118),
    onSecondaryContainer = ProxAccent2,
    tertiary = ProxGood,
    onTertiary = Color(0xFF002214),
    background = ProxBg,
    onBackground = ProxInk,
    surface = ProxSurface,
    onSurface = ProxInk,
    surfaceVariant = ProxSurface2,
    onSurfaceVariant = ProxMuted,
    surfaceContainerLowest = ProxBg,
    surfaceContainerLow = ProxSurface,
    surfaceContainer = ProxSurface2,
    surfaceContainerHigh = ProxSurface3,
    surfaceContainerHighest = Color(0xFF2A3344),
    outline = ProxLine,
    outlineVariant = Color(0xFF232B3A),
    error = ProxBad,
    onError = Color.Black,
    errorContainer = Color(0xFF3D1515),
    onErrorContainer = Color(0xFFFFB4B4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE56A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4CC),
    onPrimaryContainer = Color(0xFF3D1E00),
    secondary = Color(0xFF8A5A2B),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF12151C),
    surface = Color.White,
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFECEFF5),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFE8EBF2),
    outline = Color(0xFFD0D6E1),
    error = Color(0xFFC62828),
)

val ProxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ProxTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

object ProxSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screen = 20.dp
    val card = 16.dp
    val listGap = 12.dp
}

@Composable
fun ProxPanelTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = ProxTypography,
        shapes = ProxShapes,
        content = content,
    )
}
