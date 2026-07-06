package com.nathanb.lock.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme

@Composable
internal fun PermissionsCard(
    overlayOk: Boolean,
    accessibilityOk: Boolean,
    notificationsOk: Boolean,
    cardColors: CardColors,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val allPermissionsOk = overlayOk && accessibilityOk && notificationsOk

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 10.dp,
            bottomStart = 10.dp,
            topEnd = 40.dp,
            bottomEnd = 40.dp,
        ),
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (allPermissionsOk) colors.primary.copy(alpha = 0.15f)
                        else colors.error.copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (allPermissionsOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (allPermissionsOk) colors.primary else colors.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (allPermissionsOk) {
                        stringResource(R.string.permissions_all_granted)
                    } else {
                        buildString {
                            val missing = mutableListOf<String>()
                            if (!accessibilityOk) missing += stringResource(R.string.permissions_missing_accessibility)
                            if (!overlayOk) missing += stringResource(R.string.permissions_missing_overlay)
                            if (!notificationsOk) missing += stringResource(R.string.permissions_missing_notifications)
                            append(missing.joinToString(", ") + " " + stringResource(R.string.permissions_missing_suffix))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allPermissionsOk) {
                        colors.onSurface.copy(alpha = 0.7f)
                    } else {
                        colors.error
                    },
                )
            }
            if (!allPermissionsOk) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.error,
                )
            }
        }
    }
}
