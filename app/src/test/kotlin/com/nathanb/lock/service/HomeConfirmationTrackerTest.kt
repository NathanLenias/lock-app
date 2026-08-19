package com.nathanb.lock.service

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class HomeConfirmationTrackerTest {

    private val tracker = HomeConfirmationTracker(
        launcherPackages = setOf("com.sec.android.app.launcher"),
        ownPackage = "com.nathanb.lock",
    )

    @Test
    fun `home request starts awaiting confirmation`() {
        tracker.onHomeRequested()
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `launcher event confirms home and cancels retry`() {
        tracker.onHomeRequested()
        assertTrue(tracker.onWindowEvent("com.sec.android.app.launcher"))
        assertFalse(tracker.shouldRetry())
    }

    @Test
    fun `own overlay window does not count as confirmation`() {
        tracker.onHomeRequested()
        assertFalse(tracker.onWindowEvent("com.nathanb.lock"))
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `blocked app resurfacing does not count as confirmation`() {
        tracker.onHomeRequested()
        assertFalse(tracker.onWindowEvent("com.instagram.android"))
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `no retry when nothing was requested`() {
        assertFalse(tracker.shouldRetry())
        tracker.onWindowEvent("com.sec.android.app.launcher")
        assertFalse(tracker.shouldRetry())
    }
}
