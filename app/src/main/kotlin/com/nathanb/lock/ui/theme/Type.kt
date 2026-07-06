package com.nathanb.lock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R

val SatoshiFamily = FontFamily(
    Font(R.font.satoshi_light, FontWeight.Light),
    Font(R.font.satoshi_regular, FontWeight.Normal),
    Font(R.font.satoshi_medium, FontWeight.Medium),
    Font(R.font.satoshi_bold, FontWeight.Bold),
    Font(R.font.satoshi_black, FontWeight.Black),
)

private val DefaultTypography = Typography()

val SatoshiTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = SatoshiFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = SatoshiFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = SatoshiFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = SatoshiFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = SatoshiFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = SatoshiFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = SatoshiFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = SatoshiFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = SatoshiFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = SatoshiFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = SatoshiFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = SatoshiFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = SatoshiFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = SatoshiFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = SatoshiFamily),
)
