package com.nathanb.lock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.net.toUri
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedDomains: Set<String> = emptySet()
    private var isEmergencyPaused = false
    private var isNoEscapeSession = false
    private lateinit var overlayManager: BlockOverlayManager
    private var checkJob: Job? = null
    private var followUpJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service connected")
        overlayManager = BlockOverlayManager(this)
        // Restrict events to supported browsers (single source of truth: BrowserUrlBars).
        serviceInfo = serviceInfo.apply { packageNames = BrowserUrlBars.packages.toTypedArray() }

        val app = application as LockApplication
        scope.launch {
            app.repository.blockedDomains.collect { domains ->
                blockedDomains = domains
                if (BuildConfig.DEBUG) Log.d(TAG, "Blocked domains updated: ${domains.size}")
            }
        }
        scope.launch {
            app.repository.emergencyPause.collect { paused -> isEmergencyPaused = paused }
        }
        scope.launch {
            app.repository.lockStateFlow.collect { state -> isNoEscapeSession = state.isNoEscape }
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
            val bar = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: continue
            return blockedHostFromAddressBar(bar.text?.toString(), bar.isFocused, blockedDomains)
        }
        return null
    }

    private fun block(pkg: String, host: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Blocking $host in $pkg")
        if (!openBlankPage(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
        overlayManager.showWebsite(host, isNoEscapeSession)
        scheduleFollowUps(pkg)
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
        scope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
    }
}

/**
 * Decides from an address bar's content whether a blocked website is on screen.
 * A focused bar is being edited (the user is typing a search or an address): ignored.
 */
internal fun blockedHostFromAddressBar(text: String?, isFocused: Boolean, blockedDomains: Set<String>): String? {
    if (isFocused || text.isNullOrBlank()) return null
    val host = DomainRules.normalize(text) ?: return null
    return host.takeIf { DomainRules.matches(it, blockedDomains) }
}
