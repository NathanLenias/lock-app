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

// --- CompositionLocal for LockColors ---

val LocalLockColors = staticCompositionLocalOf { DarkLockColors }
val LocalIsDark = staticCompositionLocalOf { true }

object LockTheme {
    val colors: LockColors
        @Composable get() = LocalLockColors.current
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

    val lockColors = if (isDark) DarkLockColors else LightLockColors
    val materialScheme = if (isDark) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(
        LocalLockColors provides lockColors,
        LocalIsDark provides isDark,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = SatoshiTypography,
            content = content,
        )
    }
}
