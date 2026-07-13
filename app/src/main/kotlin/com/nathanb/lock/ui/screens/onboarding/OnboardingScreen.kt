package com.nathanb.lock.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.SetupStatus
import com.nathanb.lock.nfc.NdefWriteResult
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 7

@Composable
fun OnboardingScreen(
    viewModel: LockViewModel,
    onOnboardingComplete: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val pendingUid by viewModel.pendingPairingUid.collectAsStateWithLifecycle()
    val pairingWriteResult by viewModel.pairingWriteResult.collectAsStateWithLifecycle()
    val nfcTags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    var hasCompleted by remember { mutableStateOf(false) }
    var nfcPairingSuccess by remember { mutableStateOf(false) }

    val setupStatus = SetupStatus(
        permissionsOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            && PermissionHelper.canDrawOverlays(context),
        hasApps = profiles.firstOrNull()?.blockedPackages.orEmpty().isNotEmpty(),
        hasNfcTag = nfcTags.isNotEmpty(),
    )

    LaunchedEffect(pendingUid) {
        if (pendingUid != null && pagerState.currentPage == 5) {
            viewModel.confirmPairingWithName(pendingUid!!, context.getString(R.string.nfc_tags_default_name))
            // Trigger success animation — completion happens in onSuccessAnimationEnd
            nfcPairingSuccess = true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 5) {
            viewModel.enableNfcPairing()
        }
    }

    BackHandler(enabled = pagerState.currentPage in 1..5) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    fun goNext() {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    fun complete() {
        if (hasCompleted) return
        hasCompleted = true
        viewModel.completeSetup()
        onOnboardingComplete()
    }

    // NFC scan on completion page → lock activates → go to home
    LaunchedEffect(lockState.isLocked, pagerState.currentPage) {
        if (lockState.isLocked && pagerState.currentPage == 6) {
            complete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> WelcomePage(onNext = ::goNext)
                1 -> HowItWorksPage(onNext = ::goNext)
                2 -> WhyPhysicalPage(onNext = ::goNext)
                3 -> PermissionsPage(onNext = ::goNext)
                4 -> AppPickerPage(
                    viewModel = viewModel,
                    onNext = { selectedPackages ->
                        if (selectedPackages.isNotEmpty()) {
                            viewModel.createDefaultProfile(selectedPackages)
                        }
                        goNext()
                    },
                    onSkip = ::goNext,
                )
                5 -> NfcPairingPage(
                    isSuccess = nfcPairingSuccess,
                    // Write interrupted: the page keeps waiting, recontacting the tag retries.
                    writeInterrupted = pairingWriteResult == NdefWriteResult.TRANSIENT_FAILURE,
                    onSkip = ::goNext,
                    onSuccessAnimationEnd = ::goNext,
                )
                6 -> CompletionPage(
                    isVisible = pagerState.currentPage == 6,
                    setupStatus = setupStatus,
                    onComplete = ::complete,
                )
            }
        }

        // Hide dots on completion page
        if (pagerState.currentPage < 6) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT - 1) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == pagerState.currentPage) colors.primary
                                else if (pagerState.currentPage == 0) Color.White.copy(alpha = 0.4f)
                                else colors.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
