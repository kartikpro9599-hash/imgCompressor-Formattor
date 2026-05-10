package com.kash.imgpro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Dark-only Material 3 Color Scheme ────────────────────────────
private val ImgProDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepBlack,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = TextPrimary,

    secondary = VividViolet,
    onSecondary = DeepBlack,
    secondaryContainer = VividVioletDim,
    onSecondaryContainer = TextPrimary,

    tertiary = CoralPink,
    onTertiary = DeepBlack,
    tertiaryContainer = CoralPinkDim,
    onTertiaryContainer = TextPrimary,

    error = ErrorRed,
    onError = DeepBlack,
    errorContainer = ErrorRedDim,
    onErrorContainer = TextPrimary,

    background = DeepCharcoal,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    inverseSurface = TextPrimary,
    inverseOnSurface = DeepCharcoal,
    inversePrimary = ElectricCyanDim,

    surfaceTint = ElectricCyan,
)

@Composable
fun ImgProTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent status & nav bars for immersive dark UI
            window.statusBarColor = DeepCharcoal.toArgb()
            window.navigationBarColor = DeepBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = ImgProDarkScheme,
        typography = AppTypography,
        content = content,
    )
}
