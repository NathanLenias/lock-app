package com.nathanb.lock.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.ThemeMode

@Composable
internal fun ThemeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val options = listOf(
        Triple(ThemeMode.SYSTEM, Icons.Outlined.Contrast, stringResource(R.string.theme_auto)),
        Triple(ThemeMode.LIGHT, Icons.Outlined.LightMode, stringResource(R.string.theme_light)),
        Triple(ThemeMode.DARK, Icons.Outlined.DarkMode, stringResource(R.string.theme_dark)),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { (mode, icon, label) ->
            val selected = currentMode == mode
            val tintColor by animateColorAsState(
                targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
                animationSpec = tween(200),
                label = "themeIcon",
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clickable { onModeSelected(mode) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) colors.cardContainer else colors.surfaceContainerLow,
                border = if (selected) BorderStroke(2.dp, colors.primary.copy(alpha = 0.25f)) else null,
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = tintColor,
                    )
                }
            }
        }
    }
}
