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

    private var lastRequestPackage: String? = null
    private var lastRequestAtMs: Long = 0L

    /** A blocked app was detected and HOME was requested for [packageName] at [nowMs]. */
    fun onHomeRequested(packageName: String, nowMs: Long) {
        awaitingConfirmation = true
        lastRequestPackage = packageName
        lastRequestAtMs = nowMs
    }

    /**
     * True when HOME was already requested for this arrival of [packageName], so a second
     * press must be skipped. An app coming to the front emits two screen events 60-200 ms
     * apart (measured on a Pixel), and a launcher event can "confirm" HOME in between, so
     * the pending confirmation alone is not enough: the same package is also held back
     * for [DUPLICATE_WINDOW_MS]. Nobody reopens an app that fast by hand.
     */
    fun isDuplicateRequest(packageName: String, nowMs: Long): Boolean {
        if (awaitingConfirmation) return true
        return packageName == lastRequestPackage && nowMs - lastRequestAtMs in 0 until DUPLICATE_WINDOW_MS
    }

    /**
     * A window state change arrived. Returns true when this event confirms a
     * pending HOME request (launcher in front while we were waiting for it).
     */
    fun onWindowEvent(packageName: String): Boolean {
        if (!awaitingConfirmation) return false
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

    private companion object {
        const val DUPLICATE_WINDOW_MS = 400L
    }
}
