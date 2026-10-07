package com.nathanb.lock.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedWatchActionTest {

    @Test
    fun `stays up while the blocked app is still pinned`() {
        assertEquals(PinnedWatchAction.KEEP, pinnedWatchAction(stillPinned = true, blockingActive = true, foregroundStillBlocked = true))
    }

    @Test
    fun `unpinned blocked app is blocked the normal way`() {
        assertEquals(
            PinnedWatchAction.BLOCK_NORMALLY,
            pinnedWatchAction(stillPinned = false, blockingActive = true, foregroundStillBlocked = true),
        )
    }

    @Test
    fun `unpinned and left the blocked app just dismisses`() {
        assertEquals(PinnedWatchAction.DISMISS, pinnedWatchAction(stillPinned = false, blockingActive = true, foregroundStillBlocked = false))
    }

    @Test
    fun `session end or emergency pause frees the user even while pinned`() {
        assertEquals(PinnedWatchAction.DISMISS, pinnedWatchAction(stillPinned = true, blockingActive = false, foregroundStillBlocked = true))
    }
}
