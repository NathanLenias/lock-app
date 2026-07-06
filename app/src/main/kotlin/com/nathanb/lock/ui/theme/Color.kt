package com.nathanb.lock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// --- BrickColors: centralized design-system palette ---

@Immutable
data class BrickColors(
    val primary: Color,
    val primaryDark: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val lockedPrimary: Color,
    val cardContainer: Color,
    val lockedContainer: Color,
    val lockedOnContainer: Color,
)

val DarkBrickColors = BrickColors(
    primary = Color(0xFF73DB9A),          // Emerald
    primaryDark = Color(0xFF2F9C61),      // derived from primary
    surface = Color(0xFF100B00),          // Pitch Black (warm near-black)
    surfaceContainer = Color(0xFF1C170E), // +1
    surfaceContainerLow = Color(0xFF161108),  // +0.5
    surfaceContainerHigh = Color(0xFF241F16), // +2
    surfaceContainerHighest = Color(0xFF2C271E), // +3
    onSurface = Color(0xFFF7EDF0),       // Lavender Blush
    onSurfaceVariant = Color(0xFFE7E4E5), // Lavender Blush light
    error = Color(0xFFFFB4AB),
    cardContainer = Color(0xFF241F16),    // = surfaceContainerHigh
    lockedPrimary = Color(0xFFD32F2F),    // Tomato Jam
    lockedContainer = Color(0xFF2A0808),  // derived dark red
    lockedOnContainer = Color(0xFFF4CBC6), // Cotton Rose
)

val LightBrickColors = BrickColors(
    primary = Color(0xFF3CCD74),          // Emerald (saturated for light)
    primaryDark = Color(0xFF2BA85E),      // derived darker
    surface = Color(0xFFF9F9F9),          // Near-white neutral
    surfaceContainer = Color(0xFFEFEFEF), // +1
    surfaceContainerLow = Color(0xFFF4F4F4),  // +0.5
    surfaceContainerHigh = Color(0xFFE7E7E7), // +2
    surfaceContainerHighest = Color(0xFFDFDFDF), // +3
    onSurface = Color(0xFF1C0D11),        // Coffee Bean
    onSurfaceVariant = Color(0xFF6B5C60), // Coffee Bean lightened
    error = Color(0xFFBA1A1A),
    cardContainer = Color(0xFFFFFFFF),    // white cards on neutral bg
    lockedPrimary = Color(0xFFD32F2F),    // Tomato Jam
    lockedContainer = Color(0xFFFCEAE7), // warm light pink
    lockedOnContainer = Color(0xFF110503), // Coffee Bean 2
)
