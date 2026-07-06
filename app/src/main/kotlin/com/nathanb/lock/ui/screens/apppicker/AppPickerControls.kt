package com.nathanb.lock.ui.screens.apppicker

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

internal enum class AppCategory(val labelResId: Int, val androidCategory: Int?) {
    ALL(R.string.app_picker_category_all, null),
    SOCIAL(R.string.app_picker_category_social, ApplicationInfo.CATEGORY_SOCIAL),
    GAMES(R.string.app_picker_category_games, ApplicationInfo.CATEGORY_GAME),
    TOOLS(R.string.app_picker_category_tools, ApplicationInfo.CATEGORY_PRODUCTIVITY),
}

@Composable
internal fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = BrickTheme.colors

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = colors.surfaceContainer,
            focusedContainerColor = colors.surfaceContainer,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            cursorColor = colors.primary,
        ),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        placeholder = {
            Text(
                stringResource(R.string.app_picker_search),
                fontFamily = SatoshiFamily,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
            )
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = SatoshiFamily,
            fontSize = 14.sp,
            color = colors.onSurface,
        ),
    )
}

@Composable
internal fun CategoryPills(
    selected: AppCategory,
    onSelect: (AppCategory) -> Unit,
) {
    val colors = BrickTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppCategory.entries.forEach { category ->
            val isActive = category == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isActive) colors.primary else colors.surfaceContainer,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(category.labelResId),
                    fontFamily = SatoshiFamily,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isActive) Color.White else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun BulkActions(
    onAddAll: () -> Unit,
    onRemoveAll: () -> Unit,
) {
    val colors = BrickTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Tout ajouter
        Row(
            modifier = Modifier
                .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                .clickable(onClick = onAddAll)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.app_picker_add_all),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.primary,
            )
        }
        // Tout sortir
        Row(
            modifier = Modifier
                .background(colors.lockedContainer, RoundedCornerShape(50))
                .clickable(onClick = onRemoveAll)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = colors.lockedPrimary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.app_picker_remove_all),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = colors.lockedPrimary,
            )
        }
    }
}
