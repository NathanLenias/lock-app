package com.nathanb.lock.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.nfc.NfcResult
import com.nathanb.lock.ui.components.NfcScanCard
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel

private const val MAX_TAGS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTagsScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
    preselectProfileId: Long? = null,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val tags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()
    val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
    val defaultProfileId = defaultProfile?.id

    var isScanning by remember { mutableStateOf(false) }
    var isPairingSuccess by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var nameDialogUid by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var pickerForTag by remember { mutableStateOf<NfcTag?>(null) }

    LaunchedEffect(Unit) {
        viewModel.nfcEvents.collect { result ->
            if (result is NfcResult.TagPaired) {
                val existing = tags.find { it.uid == result.uid }
                if (existing != null) {
                    isScanning = false
                    Toast.makeText(context, context.getString(R.string.nfc_tags_already_exists, existing.name.take(50)), Toast.LENGTH_SHORT).show()
                } else {
                    val defaultName = if (tags.isEmpty()) context.getString(R.string.nfc_tags_default_name) else context.getString(R.string.nfc_tags_default_name_n, tags.size + 1)
                    if (preselectProfileId != null) {
                        viewModel.confirmPairingWithProfile(result.uid, defaultName, preselectProfileId)
                    } else {
                        viewModel.confirmPairingWithName(result.uid, defaultName)
                    }
                    isPairingSuccess = true
                }
            }
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nfc_tags_title),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = stringResource(R.string.nfc_tags_associated_count, tags.size, MAX_TAGS),
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.cardContainer)
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.label_info), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        },
        floatingActionButton = {
            if (tags.size < MAX_TAGS) {
                FloatingActionButton(
                    onClick = { isScanning = true; viewModel.enableNfcPairing() },
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.nfc_tags_add))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (isScanning || isPairingSuccess) {
                item {
                    NfcScanCard(
                        title = if (isPairingSuccess) stringResource(R.string.nfc_tags_paired) else stringResource(R.string.nfc_tags_waiting),
                        subtitle = if (isPairingSuccess) stringResource(R.string.nfc_tags_paired_subtitle) else stringResource(R.string.nfc_tags_waiting_subtitle),
                        isSuccess = isPairingSuccess,
                        ctaLabel = if (!isPairingSuccess) stringResource(R.string.action_cancel) else null,
                        onCtaClick = { isScanning = false; viewModel.nfcManager.disablePairingMode() },
                        onSuccessAnimationEnd = { isPairingSuccess = false; isScanning = false },
                    )
                }
            }

            items(tags, key = { it.uid }) { tag ->
                val profileName = profiles.find { it.id == tag.profileId }?.name ?: defaultProfile?.name.orEmpty()
                val profileType = profiles.find { it.id == (tag.profileId ?: defaultProfileId) }?.type
                TagCard(
                    tag = tag,
                    profileName = profileName,
                    profileIsNoEscape = ProfileType.fromValue(profileType) == ProfileType.NO_ESCAPE,
                    onProfileClick = { pickerForTag = tag },
                    onRename = {
                        nameDialogUid = tag.uid
                        nameInput = tag.name
                        isRenaming = true
                        showNameDialog = true
                    },
                    onDelete = { viewModel.removeNfcTag(tag.uid) },
                )
            }

            if (tags.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showHelp = true }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.nfc_info_row),
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            if (tags.isEmpty() && !isScanning) {
                item {
                    Text(
                        text = stringResource(R.string.nfc_tags_empty),
                        fontFamily = SatoshiFamily,
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Name / rename dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false; if (!isRenaming) viewModel.cancelPairing() },
            containerColor = colors.surfaceContainerHighest,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { Text(if (isRenaming) stringResource(R.string.nfc_tags_rename_title) else stringResource(R.string.nfc_tags_name_title)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 100) nameInput = it },
                    label = { Text(stringResource(R.string.nfc_tags_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
                        cursorColor = colors.primary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameInput.isNotBlank()) {
                        if (isRenaming) viewModel.renameNfcTag(nameDialogUid, nameInput.trim())
                        else viewModel.confirmPairingWithName(nameDialogUid, nameInput.trim())
                        showNameDialog = false
                    }
                }) { Text(stringResource(R.string.action_confirm), color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false; if (!isRenaming) viewModel.cancelPairing() }) {
                    Text(stringResource(R.string.action_cancel), color = colors.onSurfaceVariant)
                }
            },
        )
    }

    if (showHelp) {
        TagHelpSheet(onDismiss = { showHelp = false })
    }

    pickerForTag?.let { tag ->
        TagProfilePickerSheet(
            tag = tag,
            profiles = profiles,
            currentProfileId = tag.profileId ?: defaultProfileId,
            onSave = { id -> viewModel.assignTagToProfile(tag.uid, id); pickerForTag = null },
            onDismiss = { pickerForTag = null },
        )
    }
}

@Composable
private fun TagCard(
    tag: NfcTag,
    profileName: String,
    profileIsNoEscape: Boolean,
    onProfileClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Nfc, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tag.name, fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurface)
            // Profile dropdown chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainer)
                    .clickable(onClick = onProfileClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (profileIsNoEscape) Color(0xFFEF6C00) else colors.primary))
                Text(profileName, fontFamily = SatoshiFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = colors.onSurface)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.nfc_tags_rename_cd), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.nfc_tags_delete_cd), tint = colors.lockedPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagHelpSheet(onDismiss: () -> Unit) {
    val colors = LockTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Nfc, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(28.dp))
            }
            Text(
                text = stringResource(R.string.nfc_help_title),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.onSurface,
            )
            HelpRule(
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.nfc_help_rule1_title),
                body = stringResource(R.string.nfc_help_rule1_body),
            )
            HelpRule(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.nfc_help_rule2_title),
                body = stringResource(R.string.nfc_help_rule2_body),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(stringResource(R.string.nfc_help_cta), fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun HelpRule(icon: ImageVector, title: String, body: String) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurface)
            Text(body, fontFamily = SatoshiFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagProfilePickerSheet(
    tag: NfcTag,
    profiles: List<Profile>,
    currentProfileId: Long?,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LockTheme.colors
    var selected by remember { mutableStateOf(currentProfileId ?: profiles.firstOrNull()?.id) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.nfc_pick_profile_title), fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.onSurface)
            Text(stringResource(R.string.nfc_pick_profile_subtitle, tag.name), fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            profiles.forEach { p ->
                val isNoEscape = ProfileType.fromValue(p.type) == ProfileType.NO_ESCAPE
                val isSel = selected == p.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) colors.primary.copy(alpha = 0.08f) else colors.cardContainer)
                        .clickable { selected = p.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val iconBg = if (isNoEscape) Color(0xFFFFF1E6) else Color(0xFFE8F5E9)
                    val iconTint = if (isNoEscape) Color(0xFFEF6C00) else colors.primaryDark
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                        Icon(if (isNoEscape) Icons.Outlined.Timer else Icons.Outlined.Shield, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
                        Text(
                            stringResource(if (isNoEscape) R.string.profile_type_short_no_escape else R.string.profile_type_short_standard),
                            fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                            .background(if (isSel) colors.primary else Color.Transparent)
                            .border(2.dp, if (isSel) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { selected?.let(onSave) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(stringResource(R.string.action_save), fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
