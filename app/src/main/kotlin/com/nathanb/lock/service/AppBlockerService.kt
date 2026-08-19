package com.nathanb.lock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlockerService"

        /**
         * How long to wait for the launcher to come to the front after GLOBAL_ACTION_HOME
         * before assuming the system dropped the request (happens on some OEMs when the
         * app is launched from a notification) and sending it again.
         */
        private const val HOME_CONFIRMATION_TIMEOUT_MS = 600L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedPackages: Set<String> = emptySet()
    private var isEmergencyPaused = false
    private var isNoEscapeSession = false
    private lateinit var overlayManager: BlockOverlayManager
    private lateinit var homeTracker: HomeConfirmationTracker
    private var homeRetryJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service connected")
        overlayManager = BlockOverlayManager(this, onOkClick = ::goHome)
        homeTracker = HomeConfirmationTracker(
            launcherPackages = resolveLauncherPackages(),
            ownPackage = packageName,
        )

        val app = application as LockApplication
        scope.launch {
            app.repository.blockedPackages.collect { packages ->
                blockedPackages = packages
                if (BuildConfig.DEBUG) Log.d(TAG, "Blocked packages updated: ${packages.size} apps")
            }
        }
        scope.launch {
            app.repository.emergencyPause.collect { paused ->
                isEmergencyPaused = paused
                if (BuildConfig.DEBUG) Log.d(TAG, "Emergency pause: $paused")
            }
        }
        scope.launch {
            app.repository.lockStateFlow.collect { state ->
                isNoEscapeSession = state.isNoEscape
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (isEmergencyPaused) {
            if (overlayManager.isShowing) overlayManager.dismiss()
            return
        }

        val packageName = event.packageName?.toString() ?: return

        if (homeTracker.onWindowEvent(packageName)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Home confirmed by launcher event")
            homeRetryJob?.cancel()
            homeRetryJob = null
        }

        if (packageName in Constants.WHITELISTED_PACKAGES) return

        if (packageName in blockedPackages) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Blocking: $packageName")
            // HOME first (the real block), then overlay (visual feedback)
            goHome()
            overlayManager.show(packageName, isNoEscapeSession)
            scheduleHomeRetry()
        }
    }

    /**
     * Requests the home screen. The accessibility framework only acknowledges the
     * request; on some devices the system drops it while a launch transition or the
     * notification shade animation is running, so callers pair this with
     * [scheduleHomeRetry] or the overlay's OK button.
     */
    private fun goHome() {
        val accepted = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!accepted) {
            if (BuildConfig.DEBUG) Log.w(TAG, "GLOBAL_ACTION_HOME rejected, launching home intent")
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Retries HOME once if the launcher has not come to the front in time. */
    private fun scheduleHomeRetry() {
        homeTracker.onHomeRequested()
        homeRetryJob?.cancel()
        homeRetryJob = scope.launch {
            delay(HOME_CONFIRMATION_TIMEOUT_MS)
            if (homeTracker.shouldRetry() && !isEmergencyPaused && blockedPackages.isNotEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Home not confirmed, retrying")
                goHome()
            }
            homeTracker.reset()
        }
    }

    /**
     * The default launcher plus the known launcher packages. Resolving dynamically
     * matters because OEM launchers (Samsung, OnePlus, ...) are not in the whitelist.
     */
    private fun resolveLauncherPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return buildSet {
            resolved?.let { add(it) }
            addAll(Constants.KNOWN_LAUNCHER_PACKAGES)
        }
    }

    override fun onInterrupt() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) overlayManager.dismiss()
        homeRetryJob?.cancel()
        scope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
    }

    fun setEmergencyPause(paused: Boolean) {
        isEmergencyPaused = paused
        if (BuildConfig.DEBUG) Log.d(TAG, "Emergency pause: $paused")
    }
}
