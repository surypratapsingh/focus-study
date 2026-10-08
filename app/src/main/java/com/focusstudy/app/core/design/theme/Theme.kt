package com.focusstudy.app.core.design.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryNavy,
    onPrimaryContainer = PrimaryBlueLight,
    secondary = SecondaryTealLight,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevatedDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = DangerRedLight
)

private val MidnightColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = BackgroundMidnight,
    primaryContainer = SurfaceElevatedMidnight,
    onPrimaryContainer = PrimaryBlueLight,
    secondary = SecondaryTealLight,
    onSecondary = BackgroundMidnight,
    background = BackgroundMidnight,
    surface = SurfaceMidnight,
    surfaceVariant = SurfaceElevatedMidnight,
    onBackground = OnSurfaceMidnight,
    onSurface = OnSurfaceMidnight,
    onSurfaceVariant = OnSurfaceVariantMidnight,
    outline = OutlineMidnight,
    error = DangerRedLight
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceLight,
    primaryContainer = SurfaceElevatedLight,
    onPrimaryContainer = PrimaryNavy,
    secondary = SecondaryTeal,
    onSecondary = SurfaceLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceElevatedLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = DangerRed
)

@Composable
fun FocusStudyTheme(
    themeMode: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default false to maintain custom academic branding
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode.lowercase()) {
        "dark", "midnight" -> true
        "light" -> false
        else -> darkTheme || isSystemDark
    }
    val isMidnight = themeMode.lowercase() == "midnight"

    val colorScheme = when {
        isMidnight -> MidnightColorScheme
        isDark -> DarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusStudyTypography,
        shapes = FocusStudyShapes,
        content = content
    )
}
