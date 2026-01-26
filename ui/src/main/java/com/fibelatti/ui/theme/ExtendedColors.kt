package com.fibelatti.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// region Extended Colors
@Immutable
public data class ExtendedColors(
    val backgroundNoOverlay: Color,
)

internal val ExtendedLightColorScheme = ExtendedColors(
    backgroundNoOverlay = Color(0xFFFFFFFF),
)

internal val ExtendedDarkColorScheme = ExtendedColors(
    backgroundNoOverlay = Color(0xFF000000),
)

internal val LocalExtendedColors = staticCompositionLocalOf { ExtendedLightColorScheme }
// endregion Extended Colors

// region Material Colors
// Pinstr branding colors: Violet (#8b5cf6) to Indigo (#6366f1) gradient
internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF8b5cf6), // Violet (from Pinstr logo)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE7FF), // Light violet container
    onPrimaryContainer = Color(0xFF2E0E66), // Dark violet for contrast
    inversePrimary = Color(0xFFC7B5FF),

    secondary = Color(0xFF6366f1), // Indigo (from Pinstr gradient)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E7FF),
    onSecondaryContainer = Color(0xFF1E1B5E),

    tertiary = Color(0xFF7c3aed), // Purple accent (between violet and indigo)
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF2D0A56),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),

    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),

    surface = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE0E2EC),
    surfaceTint = Color(0xFF3F5F90),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9),

    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),

    inverseSurface = Color(0xFF2E3035),
    inverseOnSurface = Color(0xFFF0F0F7),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),

    scrim = Color(0xFF000000),

    primaryFixed = Color(0xFFD6E3FF),
    onPrimaryFixed = Color(0xFF001B3C),
    primaryFixedDim = Color(0xFFA8C8FF),
    onPrimaryFixedVariant = Color(0xFF254777),

    secondaryFixed = Color(0xFFD9E3F8),
    onSecondaryFixed = Color(0xFF121C2B),
    secondaryFixedDim = Color(0xFFBDC7DC),
    onSecondaryFixedVariant = Color(0xFF3E4758),

    tertiaryFixed = Color(0xFFE2DFFF),
    onTertiaryFixed = Color(0xFF14134A),
    tertiaryFixedDim = Color(0xFFC2C1FF),
    onTertiaryFixedVariant = Color(0xFF414178),
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC7B5FF), // Light violet for dark mode
    onPrimary = Color(0xFF2E0E66),
    primaryContainer = Color(0xFF5B21B6), // Rich violet
    onPrimaryContainer = Color(0xFFEDE7FF),
    inversePrimary = Color(0xFF8b5cf6),

    secondary = Color(0xFFA5B4FC), // Light indigo for dark mode
    onSecondary = Color(0xFF1E1B5E),
    secondaryContainer = Color(0xFF4338CA), // Rich indigo
    onSecondaryContainer = Color(0xFFE5E7FF),

    tertiary = Color(0xFFD8B4FE), // Light purple for dark mode
    onTertiary = Color(0xFF2D0A56),
    tertiaryContainer = Color(0xFF6B21A8), // Rich purple
    onTertiaryContainer = Color(0xFFF3E8FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E9),

    surface = Color(0xFF111318),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceVariant = Color(0xFF43474E),
    surfaceTint = Color(0xFFA8C8FF),

    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),

    onSurface = Color(0xFFE1E2E9),
    onSurfaceVariant = Color(0xFFC4C6CF),

    inverseSurface = Color(0xFFE1E2E9),
    inverseOnSurface = Color(0xFF2E3035),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF43474E),

    scrim = Color(0xFF000000),

    primaryFixed = Color(0xFFD6E3FF),
    onPrimaryFixed = Color(0xFF001B3C),
    primaryFixedDim = Color(0xFFA8C8FF),
    onPrimaryFixedVariant = Color(0xFF254777),

    secondaryFixed = Color(0xFFD9E3F8),
    onSecondaryFixed = Color(0xFF121C2B),
    secondaryFixedDim = Color(0xFFBDC7DC),
    onSecondaryFixedVariant = Color(0xFF3E4758),

    tertiaryFixed = Color(0xFFE2DFFF),
    onTertiaryFixed = Color(0xFF14134A),
    tertiaryFixedDim = Color(0xFFC2C1FF),
    onTertiaryFixedVariant = Color(0xFF414178),
)
// endregion Material Colors
