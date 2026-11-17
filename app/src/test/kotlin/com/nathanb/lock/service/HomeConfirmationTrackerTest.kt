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
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `launcher event confirms home and cancels retry`() {
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        assertTrue(tracker.onWindowEvent("com.sec.android.app.launcher"))
        assertFalse(tracker.shouldRetry())
    }

    @Test
    fun `own overlay window does not count as confirmation`() {
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        assertFalse(tracker.onWindowEvent("com.nathanb.lock"))
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `blocked app resurfacing does not count as confirmation`() {
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        assertFalse(tracker.onWindowEvent("com.instagram.android"))
        assertTrue(tracker.shouldRetry())
    }

    @Test
    fun `no retry when nothing was requested`() {
        assertFalse(tracker.shouldRetry())
        assertFalse(tracker.onWindowEvent("com.sec.android.app.launcher"))
        assertFalse(tracker.shouldRetry())
    }

    @Test
    fun `second screen event of the same arrival is a duplicate while home is pending`() {
        assertFalse(tracker.isDuplicateRequest("com.instagram.android", 1_000L))
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        assertTrue(tracker.isDuplicateRequest("com.instagram.android", 1_100L))
        // Any other blocked app too: HOME is already on its way.
        assertTrue(tracker.isDuplicateRequest("com.twitter.android", 1_100L))
    }

    @Test
    fun `duplicate is still held back right after an early launcher confirmation`() {
        // Seen on device: launcher event 50 ms after the block, second app event at 120 ms.
        tracker.onHomeRequested("com.twitter.android", 1_000L)
        assertTrue(tracker.onWindowEvent("com.sec.android.app.launcher"))
        assertTrue(tracker.isDuplicateRequest("com.twitter.android", 1_120L))
        // Another app is a real new arrival once HOME is confirmed.
        assertFalse(tracker.isDuplicateRequest("com.instagram.android", 1_120L))
    }

    @Test
    fun `reopening the same app later is a new arrival`() {
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        tracker.onWindowEvent("com.sec.android.app.launcher")
        assertFalse(tracker.isDuplicateRequest("com.instagram.android", 1_400L))
        assertFalse(tracker.isDuplicateRequest("com.instagram.android", 2_200L))
    }

    @Test
    fun `unconfirmed home stops holding back once the retry window resets it`() {
        tracker.onHomeRequested("com.instagram.android", 1_000L)
        tracker.reset()
        assertFalse(tracker.isDuplicateRequest("com.instagram.android", 1_600L))
    }
}
