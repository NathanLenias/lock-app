package com.nathanb.lock.service

/**
 * Tracks whether a GLOBAL_ACTION_HOME issued after a block has been confirmed.
 *
 * The accessibility service cannot query the foreground app on demand, but it does
 * receive a window event when the launcher comes to the front. That event is the
 * confirmation that HOME worked. If no such event arrives within the grace window,
 * the caller should retry HOME.
 *
 * Pure state holder so the logic can be unit tested without Android.
 */
class HomeConfirmationTracker(
    private val launcherPackages: Set<String>,
    private val ownPackage: String,
) {
    var awaitingConfirmation: Boolean = false
        private set

    /** A blocked app was detected and HOME was requested. */
    fun onHomeRequested() {
        awaitingConfirmation = true
    }

    /**
     * A window state change arrived. Returns true when this event confirms the
     * launcher is in front, which clears the pending confirmation.
     */
    fun onWindowEvent(packageName: String): Boolean {
        if (packageName == ownPackage) return false // our overlay, not informative
        if (packageName in launcherPackages) {
            awaitingConfirmation = false
            return true
        }
        return false
    }

    /** Called once the grace window elapses. True means HOME was never confirmed. */
    fun shouldRetry(): Boolean = awaitingConfirmation

    fun reset() {
        awaitingConfirmation = false
    }
}
