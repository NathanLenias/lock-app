package com.nathanb.lock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// --- Theme mode enum (persisted in DataStore) ---

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// --- CompositionLocal for BrickColors ---

val LocalBrickColors = staticCompositionLocalOf { DarkBrickColors }
val LocalIsDark = staticCompositionLocalOf { true }

object BrickTheme {
    val colors: BrickColors
        @Composable get() = LocalBrickColors.current
    val isDark: Boolean
        @Composable get() = LocalIsDark.current
}

// --- Material 3 color schemes (for MaterialTheme.typography etc.) ---

private val DarkMaterialScheme = darkColorScheme()
private val LightMaterialScheme = lightColorScheme()

@Composable
fun LockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val brickColors = if (isDark) DarkBrickColors else LightBrickColors
    val materialScheme = if (isDark) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(
        LocalBrickColors provides brickColors,
        LocalIsDark provides isDark,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = SatoshiTypography,
            content = content,
        )
    }
}
