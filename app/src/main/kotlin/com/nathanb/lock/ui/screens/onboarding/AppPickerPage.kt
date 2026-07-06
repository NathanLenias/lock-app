package com.nathanb.lock.ui.screens.onboarding

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.ui.screens.apppicker.AppCard
import com.nathanb.lock.ui.screens.apppicker.AppCategory
import com.nathanb.lock.ui.screens.apppicker.AppSearchBar
import com.nathanb.lock.ui.screens.apppicker.BlockedAppsCard
import com.nathanb.lock.ui.screens.apppicker.CategoryPills
import com.nathanb.lock.ui.screens.apppicker.SuggestionsSection
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.Constants

@Composable
internal fun AppPickerPage(
    viewModel: LockViewModel,
    onNext: (selectedPackages: List<String>) -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LockTheme.colors
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.installedAppsLoading.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    val selectedApps = remember { mutableStateMapOf<String, Boolean>() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    var blockedCardExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.ensureInstalledAppsLoaded()
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_apps_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_apps_subtitle),
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Blocked apps card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BlockedAppsCard(
                        blockedApps = blockedApps,
                        expanded = blockedCardExpanded,
                        onToggleExpand = { blockedCardExpanded = !blockedCardExpanded },
                        onRemoveApp = { pkg -> selectedApps[pkg] = false },
                    )
                }

                // Suggestions
                if (suggestions.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SuggestionsSection(
                            suggestions = suggestions,
                            onAddApp = { pkg -> selectedApps[pkg] = true },
                        )
                    }
                }

                // Search bar
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                    )
                }

                // Category pills
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryPills(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it },
                    )
                }

                // App grid
                items(filteredApps, key = { it.packageName }) { app ->
                    viewModel.getAppIcon(app.packageName)
                    AppCard(
                        app = app,
                        isSelected = selectedSnapshot[app.packageName] == true,
                        icon = iconCache[app.packageName],
                        onToggle = {
                            selectedApps[app.packageName] = selectedSnapshot[app.packageName] != true
                        },
                    )
                }

                // Bottom spacing
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Bottom CTA — hidden when keyboard is open
        AnimatedVisibility(
            visible = !imeVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingButton(
                    text = stringResource(R.string.onboarding_apps_cta, selectedApps.count { it.value }),
                    onClick = {
                        val selected = selectedApps.filter { it.value }.keys.toList()
                        onNext(selected)
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_apps_skip),
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onSkip),
                )
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}
