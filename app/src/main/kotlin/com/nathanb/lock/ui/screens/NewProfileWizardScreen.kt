package com.nathanb.lock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.ui.screens.apppicker.AppCard
import com.nathanb.lock.ui.screens.apppicker.AppCategory
import com.nathanb.lock.ui.screens.apppicker.AppSearchBar
import com.nathanb.lock.ui.screens.apppicker.CategoryPills
import com.nathanb.lock.ui.screens.profile.DURATION_OPTIONS_MS
import com.nathanb.lock.ui.screens.profile.DurationSelector
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel

private enum class WizStep { TYPE, NAME_SOURCE, APPS, DURATION, RECAP }

@Composable
fun NewProfileWizardScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val colors = LockTheme.colors
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()

    var type by remember { mutableStateOf(ProfileType.STANDARD) }
    var name by remember { mutableStateOf("") }
    var fromScratch by remember { mutableStateOf(true) }
    var copyFromId by remember { mutableStateOf<Long?>(null) }
    val selectedApps = remember { mutableStateMapOf<String, Boolean>() }
    var durationMs by remember { mutableStateOf(DURATION_OPTIONS_MS[2]) }
    var makeDefault by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableStateOf(0) }

    val isNoEscape = type == ProfileType.NO_ESCAPE

    val steps = remember(fromScratch, isNoEscape) {
        buildList {
            add(WizStep.TYPE)
            add(WizStep.NAME_SOURCE)
            if (fromScratch) add(WizStep.APPS)
            if (isNoEscape) add(WizStep.DURATION)
            add(WizStep.RECAP)
        }
    }
    val safeIndex = stepIndex.coerceIn(0, steps.lastIndex)
    val current = steps[safeIndex]
    val inputStepCount = steps.count { it != WizStep.RECAP }

    val finalApps: List<String> = if (fromScratch) {
        selectedApps.filter { it.value }.keys.toList()
    } else {
        profiles.find { it.id == copyFromId }?.blockedPackages.orEmpty()
    }

    fun goNext() {
        if (safeIndex < steps.lastIndex) stepIndex = safeIndex + 1
        else {
            viewModel.createProfileFull(
                name = name.trim(),
                type = type,
                durationMs = if (isNoEscape) durationMs else null,
                apps = finalApps,
                makeDefault = makeDefault && type == ProfileType.STANDARD,
            )
            onCreated()
        }
    }
    fun goBack() {
        if (safeIndex > 0) stepIndex = safeIndex - 1 else onBack()
    }

    val canContinue = when (current) {
        WizStep.NAME_SOURCE -> name.trim().isNotEmpty()
        else -> true
    }
    val continueLabel = when {
        current == WizStep.RECAP -> stringResource(R.string.wizard_create)
        current == WizStep.APPS -> stringResource(R.string.wizard_continue_count, finalApps.size)
        else -> stringResource(R.string.wizard_continue)
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                // Segmented progress (input steps only)
                if (current != WizStep.RECAP) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(inputStepCount) { i ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (i <= safeIndex) colors.primary else colors.surfaceContainer),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = ::goBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colors.primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (current == WizStep.RECAP) stringResource(R.string.wizard_recap_title)
                            else stringResource(R.string.wizard_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = if (current == WizStep.RECAP) {
                                stringResource(R.string.wizard_recap_subtitle)
                            } else {
                                stringResource(R.string.wizard_step_counter, safeIndex + 1, inputStepCount)
                            },
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = ::goNext,
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    if (current == WizStep.RECAP) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = continueLabel,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                WizStep.TYPE -> StepType(type = type, onSelect = { type = it })
                WizStep.NAME_SOURCE -> StepNameSource(
                    viewModel = viewModel,
                    profiles = profiles,
                    name = name,
                    onNameChange = { name = it },
                    fromScratch = fromScratch,
                    copyFromId = copyFromId,
                    onSelectScratch = { fromScratch = true; copyFromId = null },
                    onSelectCopy = { id -> fromScratch = false; copyFromId = id },
                )
                WizStep.APPS -> StepApps(viewModel = viewModel, selectedApps = selectedApps)
                WizStep.DURATION -> StepDuration(durationMs = durationMs, onSelect = { durationMs = it })
                WizStep.RECAP -> StepRecap(
                    name = name,
                    isNoEscape = isNoEscape,
                    appCount = finalApps.size,
                    durationMinutes = (durationMs / 60_000L).toInt(),
                    makeDefault = makeDefault,
                    onToggleDefault = { makeDefault = it },
                )
            }
        }
    }
}

@Composable
private fun StepType(type: ProfileType, onSelect: (ProfileType) -> Unit) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        WizLabel(stringResource(R.string.wizard_type_label))
        TypeChoiceCard(
            selected = type == ProfileType.STANDARD,
            icon = Icons.Outlined.Shield,
            iconBg = Color(0xFFE8F5E9),
            iconTint = colors.primaryDark,
            title = stringResource(R.string.wizard_type_normal_title),
            subtitle = stringResource(R.string.wizard_type_normal_subtitle),
            badge = null,
            onClick = { onSelect(ProfileType.STANDARD) },
        )
        TypeChoiceCard(
            selected = type == ProfileType.NO_ESCAPE,
            icon = Icons.Outlined.Lock,
            iconBg = Color(0xFFFFF1E6),
            iconTint = Color(0xFFEF6C00),
            title = stringResource(R.string.wizard_type_no_escape_title),
            subtitle = stringResource(R.string.wizard_type_no_escape_subtitle),
            badge = stringResource(R.string.wizard_type_no_escape_badge),
            onClick = { onSelect(ProfileType.NO_ESCAPE) },
        )
    }
}

@Composable
private fun TypeChoiceCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.08f) else colors.cardContainer)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFF1E6))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(badge, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color(0xFFEF6C00))
                    }
                }
            }
            Text(
                text = subtitle,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StepNameSource(
    viewModel: LockViewModel,
    profiles: List<Profile>,
    name: String,
    onNameChange: (String) -> Unit,
    fromScratch: Boolean,
    copyFromId: Long?,
    onSelectScratch: () -> Unit,
    onSelectCopy: (Long) -> Unit,
) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        WizLabel(stringResource(R.string.wizard_name_label))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            },
        )
        Spacer(Modifier.height(6.dp))
        WizLabel(stringResource(R.string.wizard_source_label))
        SourceRow(
            selected = fromScratch,
            title = stringResource(R.string.wizard_source_scratch_title),
            subtitle = stringResource(R.string.wizard_source_scratch_subtitle),
            icons = emptyList(),
            onClick = onSelectScratch,
        )
        profiles.forEach { p ->
            val icons = p.blockedPackages.take(4).mapNotNull { viewModel.getAppIcon(it) }
            SourceRow(
                selected = !fromScratch && copyFromId == p.id,
                title = stringResource(R.string.wizard_source_copy_title, p.name),
                subtitle = stringResource(R.string.wizard_source_copy_subtitle, p.blockedPackages.size),
                icons = icons,
                onClick = { onSelectCopy(p.id) },
            )
        }
    }
}

@Composable
private fun SourceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    icons: List<androidx.compose.ui.graphics.ImageBitmap>,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
            Text(subtitle, fontFamily = SatoshiFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
        if (icons.isNotEmpty()) {
            val overlap = 16
            Box(modifier = Modifier.width((22 + (icons.size - 1) * overlap).dp).height(22.dp)) {
                icons.forEachIndexed { i, bmp ->
                    Image(bitmap = bmp, contentDescription = null, modifier = Modifier.offset(x = (i * overlap).dp).size(22.dp).clip(CircleShape))
                }
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else Color.Transparent)
                .border(2.dp, if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StepApps(
    viewModel: LockViewModel,
    selectedApps: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
    val colors = LockTheme.colors
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }

    LaunchedEffect(Unit) { viewModel.ensureInstalledAppsLoaded() }

    val snapshot = selectedApps.toMap()
    val filteredApps = remember(searchQuery, selectedCategory, installedApps) {
        var apps = installedApps
        if (searchQuery.isNotBlank()) {
            apps = apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        if (selectedCategory != AppCategory.ALL) {
            apps = apps.filter { it.category == selectedCategory.androidCategory }
        }
        apps
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WizLabel(stringResource(R.string.wizard_recap_apps))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.wizard_recap_apps_value, snapshot.count { it.value }),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            CategoryPills(selected = selectedCategory, onSelect = { selectedCategory = it })
        }
        items(filteredApps, key = { it.packageName }) { app ->
            viewModel.getAppIcon(app.packageName)
            AppCard(
                app = app,
                isSelected = snapshot[app.packageName] == true,
                icon = iconCache[app.packageName],
                onToggle = { selectedApps[app.packageName] = snapshot[app.packageName] != true },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StepDuration(durationMs: Long, onSelect: (Long) -> Unit) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFF6EC))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.wizard_duration_info),
                fontFamily = SatoshiFamily,
                fontSize = 13.sp,
                color = Color(0xFFB45309),
            )
        }
        WizLabel(stringResource(R.string.wizard_duration_label))
        DurationSelector(selectedMs = durationMs, onSelect = onSelect)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.lockedPrimary.copy(alpha = 0.08f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.lockedPrimary, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.wizard_duration_warning),
                fontFamily = SatoshiFamily,
                fontSize = 13.sp,
                color = colors.lockedPrimary,
            )
        }
    }
}

@Composable
private fun StepRecap(
    name: String,
    isNoEscape: Boolean,
    appCount: Int,
    durationMinutes: Int,
    makeDefault: Boolean,
    onToggleDefault: (Boolean) -> Unit,
) {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardContainer),
        ) {
            RecapRow(stringResource(R.string.wizard_recap_name), name, valueIsBadge = false, badgeNoEscape = false)
            RecapRow(
                stringResource(R.string.wizard_recap_type),
                if (isNoEscape) stringResource(R.string.profile_badge_no_escape) else stringResource(R.string.wizard_type_normal_title),
                valueIsBadge = isNoEscape,
                badgeNoEscape = true,
            )
            RecapRow(stringResource(R.string.wizard_recap_apps), stringResource(R.string.wizard_recap_apps_value, appCount), valueIsBadge = false, badgeNoEscape = false)
            if (isNoEscape) {
                RecapRow(stringResource(R.string.wizard_recap_duration), stringResource(R.string.profile_duration_minutes, durationMinutes), valueIsBadge = false, badgeNoEscape = false)
            }
        }

        // "Set as default" only for standard profiles
        if (!isNoEscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wizard_recap_default), fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurface)
                    Text(stringResource(R.string.wizard_recap_default_subtitle), fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked = makeDefault,
                    onCheckedChange = onToggleDefault,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.cardContainer),
                )
            }
        }
    }
}

@Composable
private fun RecapRow(label: String, value: String, valueIsBadge: Boolean, badgeNoEscape: Boolean) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = SatoshiFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (valueIsBadge) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.lockedPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(value, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = colors.lockedPrimary)
            }
        } else {
            Text(value, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.onSurface)
        }
    }
}

@Composable
private fun WizLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = LockTheme.colors.onSurfaceVariant,
    )
}
