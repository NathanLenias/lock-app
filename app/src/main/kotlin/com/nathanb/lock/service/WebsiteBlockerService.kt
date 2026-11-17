package com.nathanb.lock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.net.toUri
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.util.AppPinning
import com.nathanb.lock.util.DomainRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional second accessibility service ("Lock: websites"): reads the address bar of known
 * browsers and leaves blocked websites. Kept apart from [AppBlockerService] so that only
 * users who want website blocking grant window-content access.
 *
 * Only listens to [BrowserUrlBars.packages], and reads nothing while no session blocks a
 * website. On a blocked host: open a blank page in the same browser (falls back to BACK),
 * show the block overlay, then re-check twice because some browsers restore the tab.
 */
class WebsiteBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "WebsiteBlockerService"

        /** Batches the burst of content events a page load emits into one read. */
        private const val FIRST_READ_DELAY_MS = 300L

        /** The host must stay the same this long: redirects and typing pass through. */
        private const val STABILITY_MS = 700L

        private const val FOLLOW_UP_FIRST_MS = 650L
        private const val FOLLOW_UP_FINAL_MS = 1_250L

        /** Upper bound on the nodes visited when looking for a Compose address bar. */
        private const val COMPOSE_SEARCH_MAX_NODES = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedDomains: Set<String> = emptySet()
    private var isEmergencyPaused = false
    private var isNoEscapeSession = false
    private var sessionStartTime: Long? = null
    private val attemptCounter = BlockAttemptCounter()
    private lateinit var overlayManager: BlockOverlayManager
    private lateinit var unlockDeferral: UnlockDeferral
    private var checkJob: Job? = null
    private var followUpJob: Job? = null
    private var pinnedWatchJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service connected")
        overlayManager = BlockOverlayManager(this)
        unlockDeferral = UnlockDeferral(this) { checkForegroundBrowser() }.also { it.register() }
        // Restrict events to supported browsers (single source of truth: BrowserUrlBars).
        serviceInfo = serviceInfo.apply { packageNames = BrowserUrlBars.packages.toTypedArray() }

        val app = application as LockApplication
        scope.launch {
            app.repository.blockedDomains.collect { domains ->
                blockedDomains = domains
                if (BuildConfig.DEBUG) Log.d(TAG, "Blocked domains updated: ${domains.size}")
                checkForegroundBrowser()
            }
        }
        scope.launch {
            app.repository.emergencyPause.collect { paused ->
                val resumed = isEmergencyPaused && !paused
                isEmergencyPaused = paused
                if (resumed) checkForegroundBrowser()
            }
        }
        scope.launch {
            app.repository.lockStateFlow.collect { state ->
                isNoEscapeSession = state.isNoEscape
                sessionStartTime = state.sessionStartTime
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nothing is read while no session blocks a website.
        if (blockedDomains.isEmpty()) return
        if (isEmergencyPaused) {
            if (overlayManager.isShowing) overlayManager.dismiss()
            return
        }
        val pkg = event?.packageName?.toString() ?: return
        if (!BrowserUrlBars.isSupported(pkg)) return
        scheduleCheck(pkg)
    }

    /**
     * Blocking only reacts to browser events. When it starts again while a blocked site is
     * already on screen (end of an emergency pause, a schedule window starting), the page
     * may stay still, so the browser in front is checked here.
     */
    private fun checkForegroundBrowser() {
        if (blockedDomains.isEmpty() || isEmergencyPaused) return
        if (!unlockDeferral.userIsPresent()) return
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (BrowserUrlBars.isSupported(pkg)) scheduleCheck(pkg)
    }

    private fun scheduleCheck(pkg: String) {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            delay(FIRST_READ_DELAY_MS)
            val host = readBlockedHost(pkg) ?: return@launch
            delay(STABILITY_MS)
            if (readBlockedHost(pkg) != host) return@launch
            block(pkg, host)
        }
    }

    /** The blocked host currently shown by [pkg]'s address bar, or null. */
    private fun readBlockedHost(pkg: String): String? {
        if (blockedDomains.isEmpty() || isEmergencyPaused) return null
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != pkg) return null
        for (id in BrowserUrlBars.idsFor(pkg)) {
            val bar = findAddressBar(root, pkg, id) ?: continue
            val content = addressBarContent(bar.text?.toString(), bar.contentDescription?.toString())
            return blockedHostFromAddressBar(content, bar.isFocused, blockedDomains)
        }
        return null
    }

    private fun findAddressBar(root: AccessibilityNodeInfo, pkg: String, id: String): AccessibilityNodeInfo? {
        if (!BrowserUrlBars.isComposeTag(id)) return root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
        // Start from the toolbar's host view so the page content is never walked.
        val start = root.findAccessibilityNodeInfosByViewId(BrowserUrlBars.composeToolbarId(pkg)).firstOrNull() ?: root
        return findByComposeTag(start, id)
    }

    /** Breadth-first search for the node whose resource name is the Compose test tag [tag]. */
    private fun findByComposeTag(start: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(start) }
        var visited = 0
        while (queue.isNotEmpty() && visited < COMPOSE_SEARCH_MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.viewIdResourceName == tag) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun block(pkg: String, host: String) {
        if (pinnedWatchJob?.isActive == true) return // pinned overlay already up
        if (BuildConfig.DEBUG) Log.d(TAG, "Blocking $host in $pkg")
        if (AppPinning.isActive(this)) {
            // Pinned: the blank page is a new task, refused by Android. BACK stays in the app.
            performGlobalAction(GLOBAL_ACTION_BACK)
            blockPinned(pkg, host)
            return
        }
        if (!openBlankPage(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
        overlayManager.showWebsite(host, isNoEscapeSession, attemptCounter.record(sessionStartTime, host))
        scheduleFollowUps(pkg)
    }

    /**
     * The browser is pinned: the HOME fallback is refused, so the overlay stays up (no OK
     * button) until the user unpins it, the session ends or an emergency pause starts.
     */
    private fun blockPinned(pkg: String, host: String) {
        overlayManager.showPinned(host)
        if (pinnedWatchJob?.isActive == true) return
        if (BuildConfig.DEBUG) Log.d(TAG, "Browser is pinned: $pkg")
        pinnedWatchJob = scope.watchPinnedBlock(
            isPinned = { AppPinning.isActive(this) },
            blockingActive = { blockedDomains.isNotEmpty() && !isEmergencyPaused },
            foregroundStillBlocked = { readBlockedHost(pkg) != null },
            onDismiss = { overlayManager.dismiss() },
            onBlockNormally = { readBlockedHost(pkg)?.let { block(pkg, it) } },
        )
    }

    /**
     * Some browsers restore the blocked tab right after the blank page. Re-check twice;
     * on the last check, leave the browser for good (BACK then HOME).
     */
    private fun scheduleFollowUps(pkg: String) {
        followUpJob?.cancel()
        followUpJob = scope.launch {
            delay(FOLLOW_UP_FIRST_MS)
            if (readBlockedHost(pkg) != null && !openBlankPage(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
            delay(FOLLOW_UP_FINAL_MS)
            if (readBlockedHost(pkg) != null) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Blocked tab still visible, leaving $pkg")
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    /** Replaces the page with about:blank in the same browser. False if the browser refuses. */
    private fun openBlankPage(pkg: String): Boolean = runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, "about:blank".toUri())
                .setPackage(pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
    }.onFailure { if (BuildConfig.DEBUG) Log.w(TAG, "Blank page refused by $pkg", it) }.isSuccess

    override fun onInterrupt() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) overlayManager.dismiss()
        if (::unlockDeferral.isInitialized) unlockDeferral.unregister()
        pinnedWatchJob?.cancel()
        scope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
    }
}

/**
 * What an address bar node shows. Classic bars carry the address in their text. Compose
 * bars leave it empty and describe themselves instead ("example.org/page. Search or
 * address", hint localized): the address is the first word of that description, minus the
 * full stop that ends the sentence.
 */
internal fun addressBarContent(text: String?, description: String?): String? {
    if (!text.isNullOrBlank()) return text
    val firstWord = description?.trim()?.split(WHITESPACE, limit = 2)?.firstOrNull()
    return firstWord?.removeSuffix(".")?.takeIf { it.isNotEmpty() }
}

private val WHITESPACE = Regex("\\s+")

/**
 * Decides from an address bar's content whether a blocked website is on screen.
 * A focused bar is being edited (the user is typing a search or an address): ignored.
 */
internal fun blockedHostFromAddressBar(text: String?, isFocused: Boolean, blockedDomains: Set<String>): String? {
    if (isFocused || text.isNullOrBlank()) return null
    val host = DomainRules.normalize(text) ?: return null
    return host.takeIf { DomainRules.matches(it, blockedDomains) }
}
