package com.nathanb.lock.ui.screens.apppicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.InstalledApp

@Composable
internal fun SuggestionsSection(
    suggestions: List<InstalledApp>,
    onAddApp: (String) -> Unit,
) {
    val colors = LockTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.app_picker_suggestions),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )

        // Grid 3xN rows
        suggestions.chunked(3).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowApps.forEach { app ->
                    SuggestionChip(
                        label = app.label,
                        onClick = { onAddApp(app.packageName) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill remaining slots if less than 3
                repeat(3 - rowApps.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors

    Row(
        modifier = modifier
            .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Green circle with +
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(colors.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            label,
            fontFamily = SatoshiFamily,
            fontSize = 13.sp,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
