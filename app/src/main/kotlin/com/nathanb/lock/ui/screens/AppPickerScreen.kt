package com.nathanb.lock.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.nathanb.lock.ui.components.LockBottomSheet
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.ui.screens.apppicker.AppCard
import com.nathanb.lock.ui.screens.apppicker.AppCategory
import com.nathanb.lock.ui.screens.apppicker.AppSearchBar
import com.nathanb.lock.ui.screens.apppicker.BlockedAppsCard
import com.nathanb.lock.ui.screens.apppicker.BulkActions
import com.nathanb.lock.ui.screens.apppicker.CategoryPills
import com.nathanb.lock.ui.screens.apppicker.SuggestionsSection
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.util.Constants
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel

@Composable
fun AppPickerScreen(
    viewModel: LockViewModel,
    profileId: Long,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.installedAppsLoading.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    val selectedApps = remember { mutableStateMapOf<String, Boolean>() }

    var selectionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(installedApps, profiles) {
        if (!selectionInitialized && installedApps.isNotEmpty()) {
            val blockedSet = profiles.find { it.id == profileId }?.blockedPackages?.toSet().orEmpty()
            blockedSet.forEach { pkg -> selectedApps[pkg] = true }
            selectionInitialized = true
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    var blockedCardExpanded by remember { mutableStateOf(true) }
    // Blocking Android Settings is opt-in and confirmed: it removes the usual way out.
    var showSettingsWarning by remember { mutableStateOf(false) }

    val selectedSnapshot = selectedApps.toMap()
    val blockedApps = remember(installedApps, selectedSnapshot) {
        installedApps.filter { selectedSnapshot[it.packageName] == true }
    }

    // Suggestions: curated popular apps first, then fill with social/games from device
    val suggestions = remember(installedApps, selectedSnapshot) {
        val notBlocked = installedApps.filter { selectedSnapshot[it.packageName] != true }
        val installedPkgs = notBlocked.map { it.packageName }.toSet()

        // Pass 1: curated list of commonly distracting apps
        val curated = Constants.CURATED_SUGGESTIONS.filter { it in installedPkgs }
        val curatedApps = curated.mapNotNull { pkg -> notBlocked.find { it.packageName == pkg } }

        // Pass 2: fill remaining slots from social/games categories
        val curatedSet = curated.toSet()
        val categoryApps = notBlocked
            .filter { it.packageName !in curatedSet }
            .filter {
                it.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    it.category == ApplicationInfo.CATEGORY_GAME
            }

        (curatedApps + categoryApps).take(6)
    }

    // Filtered grid apps
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

    val gridState = rememberLazyGridState()

    // Scroll to search bar when keyboard appears
    val searchBarIndex = 2 + (if (suggestions.isNotEmpty()) 1 else 0)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            gridState.animateScrollToItem(searchBarIndex)
        }
    }

    fun save() {
        val selected = selectedApps.filter { it.value }.keys.toList()
        viewModel.updateProfileApps(profileId, selected)
        onBack()
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.onSurface,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onBack),
                )
                Text(
                    stringResource(R.string.app_picker_title),
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.action_save),
                    tint = colors.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = ::save),
                )
            }
        },
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Spacing top
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(6.dp))
                }

                // --- Blocked apps card ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BlockedAppsCard(
                        blockedApps = blockedApps,
                        expanded = blockedCardExpanded,
                        onToggleExpand = { blockedCardExpanded = !blockedCardExpanded },
                        onRemoveApp = { pkg -> selectedApps[pkg] = false },
                    )
                }

                // --- Suggestions ---
                if (suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SuggestionsSection(
                            suggestions = suggestions,
                            onAddApp = { pkg -> selectedApps[pkg] = true },
                        )
                    }
                }

                // --- Search bar ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                    )
                }

                // --- Category pills ---
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryPills(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it },
                    )
                }

                // --- Bulk actions (ajouter tout / tout sortir) ---
                if (selectedCategory != AppCategory.ALL) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BulkActions(
                            onAddAll = {
                                // Settings only joins through its own card and confirmation.
                                filteredApps
                                    .filter { it.packageName != Constants.SETTINGS_PACKAGE }
                                    .forEach { app -> selectedApps[app.packageName] = true }
                            },
                            onRemoveAll = {
                                filteredApps.forEach { app ->
                                    selectedApps[app.packageName] = false
                                }
                            },
                        )
                    }
                }

                // --- App grid ---
                items(filteredApps, key = { it.packageName }) { app ->
                    // Trigger icon loading
                    viewModel.getAppIcon(app.packageName)
                    AppCard(
                        app = app,
                        isSelected = selectedSnapshot[app.packageName] == true,
                        icon = iconCache[app.packageName],
                        onToggle = {
                            val select = selectedSnapshot[app.packageName] != true
                            if (select && app.packageName == Constants.SETTINGS_PACKAGE) {
                                showSettingsWarning = true
                            } else {
                                selectedApps[app.packageName] = select
                            }
                        },
                    )
                }

                // Bottom spacing
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showSettingsWarning) {
        LockBottomSheet(
            onDismiss = { showSettingsWarning = false },
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.app_picker_settings_warning_title),
            body = stringResource(R.string.app_picker_settings_warning_body),
            actions = {
                Button(
                    onClick = {
                        selectedApps[Constants.SETTINGS_PACKAGE] = true
                        showSettingsWarning = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = stringResource(R.string.app_picker_settings_warning_confirm),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showSettingsWarning = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontFamily = SatoshiFamily,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
