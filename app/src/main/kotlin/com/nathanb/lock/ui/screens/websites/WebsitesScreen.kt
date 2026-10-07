package com.nathanb.lock.ui.screens.websites

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.ui.screens.SectionLabel
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.DomainRules
import com.nathanb.lock.util.PermissionHelper

private sealed interface InputError {
    data class Invalid(val text: String) : InputError
    data class Duplicate(val host: String) : InputError
}

/** List of a profile's blocked websites. Editable whether or not the service is on. */
@Composable
fun WebsitesScreen(
    viewModel: LockViewModel,
    profileId: Long,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()
    val profile = profiles.find { it.id == profileId }

    LaunchedEffect(profile == null) {
        if (profile == null) onBack()
    }
    if (profile == null) return

    val domains = profile.blockedDomains
    val serviceOn = rememberWebsiteServiceEnabled()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<InputError?>(null) }

    fun add() {
        val host = DomainRules.normalize(input)
        error = when {
            host == null -> InputError.Invalid(input.trim())
            host in domains -> InputError.Duplicate(host)
            else -> null
        }
        if (host != null && error == null) {
            viewModel.updateProfileDomains(profileId, domains + host)
            input = ""
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            WebsitesTopBar(
                subtitle = stringResource(R.string.websites_profile_label, profile.name) +
                    if (domains.isEmpty()) "" else " · " + pluralStringResource(R.plurals.websites_count, domains.size, domains.size),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (!serviceOn && domains.isNotEmpty()) {
                PermissionOffBanner(onClick = { PermissionHelper.openAccessibilitySettings(context) })
            }

            // Input
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = error != null,
                        placeholder = {
                            Text(stringResource(R.string.websites_input_placeholder), fontFamily = SatoshiFamily, fontSize = 16.sp)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Link,
                                contentDescription = null,
                                tint = if (error != null) colors.lockedPrimary else colors.onSurfaceVariant,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = SatoshiFamily),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) add() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.2f),
                            errorBorderColor = colors.lockedPrimary,
                            cursorColor = colors.primary,
                            errorCursorColor = colors.lockedPrimary,
                            focusedContainerColor = colors.cardContainer,
                            unfocusedContainerColor = colors.cardContainer,
                            errorContainerColor = colors.cardContainer,
                        ),
                    )
                    val canAdd = input.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canAdd) colors.primary else colors.primary.copy(alpha = 0.4f))
                            .clickable(enabled = canAdd) { add() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.websites_add),
                            tint = colors.cardContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                when (val e = error) {
                    null -> Text(
                        text = stringResource(R.string.websites_subdomains_hint),
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.lockedPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = when (e) {
                                is InputError.Invalid -> stringResource(R.string.websites_error_invalid, e.text)
                                is InputError.Duplicate -> stringResource(R.string.websites_error_duplicate, e.host)
                            },
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.lockedPrimary,
                        )
                    }
                }
            }

            if (domains.isEmpty()) {
                EmptyState()
            } else {
                SectionLabel(stringResource(R.string.websites_section_blocked))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardContainer),
                ) {
                    domains.forEachIndexed { index, host ->
                        if (index > 0) HorizontalDivider(color = colors.onSurfaceVariant.copy(alpha = 0.08f))
                        WebsiteRow(
                            host = host,
                            active = serviceOn,
                            onRemove = { viewModel.updateProfileDomains(profileId, domains - host) },
                        )
                    }
                }
            }

            // Service off: the banner above takes over once there is something to block.
            if (serviceOn) {
                ServiceOnFooter(onManage = { PermissionHelper.openAccessibilitySettings(context) })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun WebsitesTopBar(subtitle: String, onBack: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = colors.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.websites_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                fontFamily = SatoshiFamily,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** True when "Lock: websites" is enabled in Android settings, re-checked on every resume. */
@Composable
internal fun rememberWebsiteServiceEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(PermissionHelper.isWebsiteServiceEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            enabled = PermissionHelper.isWebsiteServiceEnabled(context)
        }
    }
    return enabled
}

@Composable
private fun PermissionOffBanner(onClick: () -> Unit) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.warningContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.websites_banner_title),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.websites_banner_body, stringResource(R.string.website_service_label)),
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.warning),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = colors.onWarning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.websites_banner_cta),
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onWarning,
            )
        }
    }
}

@Composable
private fun WebsiteRow(host: String, active: Boolean, onRemove: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) colors.primary.copy(alpha = 0.1f) else colors.onSurfaceVariant.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = host,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (active) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = 0.08f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.websites_remove, host),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardContainer)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.websites_empty_title),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.websites_empty_body),
            fontFamily = SatoshiFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServiceOnFooter(onManage: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.primary))
        Text(
            text = stringResource(R.string.websites_status_on),
            fontFamily = SatoshiFamily,
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.websites_status_manage),
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onManage).padding(4.dp),
        )
    }
}
