package com.nathanb.lock.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

@Composable
fun TypeCard(
    isNoEscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = BrickTheme.colors
    // Translucent accent tint so the card + adaptive text read in both light and dark themes.
    val accent = if (isNoEscape) Color(0xFFEF6C00) else colors.primary
    val bg = accent.copy(alpha = 0.12f)
    val tint = accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isNoEscape) Icons.Outlined.Timer else Icons.Outlined.Shield,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    if (isNoEscape) R.string.profile_type_no_escape_title
                    else R.string.profile_type_standard_title,
                ),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(
                    if (isNoEscape) R.string.profile_type_no_escape_subtitle
                    else R.string.profile_type_standard_subtitle,
                ),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
