package com.nathanb.lock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme

private enum class NavTab(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("home", R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Stats("stats", R.string.nav_stats, Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    Settings("settings", R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
fun FloatingNavBar(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val colors = LockTheme.colors
    val navBarBackground = colors.surfaceContainer.copy(alpha = 0.95f)

    if (vertical) {
        // Landscape: same pill, stacked on the left edge, vertically centered by the caller.
        Surface(
            modifier = modifier.padding(start = 16.dp),
            shape = RoundedCornerShape(50),
            color = navBarBackground,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NavTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavBarItem(
                        label = stringResource(tab.labelResId),
                        icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        selected = selected,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        shape = RoundedCornerShape(50),
        color = navBarBackground,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                NavBarItem(
                    label = stringResource(tab.labelResId),
                    icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    selected = selected,
                    onClick = { onTabSelected(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    val selectedBg = colors.primary.copy(alpha = 0.12f)
    val selectedContent = colors.primary
    val unselectedContent = colors.onSurfaceVariant.copy(alpha = 0.7f)

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navItemBg",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedContent else unselectedContent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navItemColor",
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(50),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
