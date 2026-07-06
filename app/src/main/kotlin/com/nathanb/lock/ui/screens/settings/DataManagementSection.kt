package com.nathanb.lock.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DataManagementSection(
    cardColors: CardColors,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = LockTheme.colors
    var showDataInfoSheet by remember { mutableStateOf(false) }

    // Header with info button
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.data_management_title),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
            letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { showDataInfoSheet = true },
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.data_info_cd),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    // Export / Import buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            onClick = onExport,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(40.dp),
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    Icons.Default.Upload,
                    contentDescription = null,
                    tint = colors.primary,
                )
                Text(
                    text = stringResource(R.string.data_export),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Card(
            onClick = onImport,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(40.dp),
            colors = cardColors,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = colors.primary,
                )
                Text(
                    text = stringResource(R.string.data_import),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    // Info bottom sheet
    if (showDataInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDataInfoSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = stringResource(R.string.data_info_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.data_info_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
