package com.nathanb.lock.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme

internal val ManualOrange = Color(0xFFE6A04C)

@Composable
fun ManualModeCard(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BrickTheme.colors
    val isDark = colors.surface.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF2A1A08) else Color(0xFFFFF8F0)
    val cardBorder = if (isDark) Color(0xFF4A3018) else Color(0xFFF0E0CC)
    val textColor = colors.onSurface
    val subtextColor = colors.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardBg, RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = cardBorder,
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PanTool,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ManualOrange,
            )
            Text(
                text = stringResource(R.string.manual_mode_card_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }

        Text(
            text = stringResource(R.string.manual_mode_card_desc),
            fontSize = 13.sp,
            color = subtextColor,
            lineHeight = 18.sp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ManualOrange,
            )
            Text(
                text = stringResource(R.string.manual_mode_card_warning),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ManualOrange,
            )
        }

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ManualOrange,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.manual_mode_card_cta),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
