package com.nathanb.lock.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlockerService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var blockedPackages: Set<String> = emptySet()
    private var isEmergencyPaused = false
    private var isNoEscapeSession = false
    private lateinit var overlayManager: BlockOverlayManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service connected")
        overlayManager = BlockOverlayManager(this)

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

        if (packageName in Constants.WHITELISTED_PACKAGES) return

        if (packageName in blockedPackages) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Blocking: $packageName")
            // HOME first (the real block), then overlay (visual feedback)
            performGlobalAction(GLOBAL_ACTION_HOME)
            overlayManager.show(packageName, isNoEscapeSession)
        }
    }

    override fun onInterrupt() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) overlayManager.dismiss()
        scope.cancel()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
    }

    fun setEmergencyPause(paused: Boolean) {
        isEmergencyPaused = paused
        if (BuildConfig.DEBUG) Log.d(TAG, "Emergency pause: $paused")
    }
}
