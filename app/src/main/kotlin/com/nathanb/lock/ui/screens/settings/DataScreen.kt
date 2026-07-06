package com.nathanb.lock.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nathanb.lock.R
import com.nathanb.lock.data.backup.BackupManager
import com.nathanb.lock.ui.theme.BrickTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel

@Composable
fun DataScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
) {
    val colors = BrickTheme.colors
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backupEvents.collect { event ->
            when (event) {
                is LockViewModel.BackupEvent.ExportSuccess -> {
                    Toast.makeText(context, context.getString(R.string.data_export_success), Toast.LENGTH_LONG).show()
                }
                is LockViewModel.BackupEvent.ImportSuccess -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.data_import_success, event.profileCount, event.tagCount),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is LockViewModel.BackupEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(54.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.primary,
                )
            }
            Text(
                text = stringResource(R.string.data_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(22.dp))

        // Privacy info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.primary.copy(alpha = 0.07f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.data_privacy_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.data_privacy_scope),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Data card
        SettingsCard {
            SettingsRow(
                icon = Icons.Outlined.FileUpload,
                title = stringResource(R.string.data_export),
                subtitle = stringResource(R.string.data_export_subtitle),
                onClick = { exportLauncher.launch(BackupManager.suggestFileName()) },
            )

            SettingsDivider()

            SettingsRow(
                icon = Icons.Outlined.FileDownload,
                title = stringResource(R.string.data_import),
                subtitle = stringResource(R.string.data_import_subtitle),
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )

        }
    }
}
