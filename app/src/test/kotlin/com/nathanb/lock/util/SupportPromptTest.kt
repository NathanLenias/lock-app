package com.nathanb.lock.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SupportPromptTest {
    @Test
    fun `decline moves to the next multiple of 10 above the count`() {
        assertEquals(10, SupportPrompt.nextAfterDecline(3))
        assertEquals(20, SupportPrompt.nextAfterDecline(10))
        assertEquals(30, SupportPrompt.nextAfterDecline(25))
        assertEquals(110, SupportPrompt.nextAfterDecline(105))
    }

    @Test
    fun `support re-asks 20 sessions later`() {
        assertEquals(32, SupportPrompt.nextAfterSupport(12))
        assertEquals(23, SupportPrompt.nextAfterSupport(3))
    }

    @Test
    fun `legacy stages map to their old milestones`() {
        assertEquals(3, SupportPrompt.fromLegacyStage(null))
        assertEquals(3, SupportPrompt.fromLegacyStage(0))
        assertEquals(20, SupportPrompt.fromLegacyStage(2))
        assertEquals(40, SupportPrompt.fromLegacyStage(4))
        assertEquals(Int.MAX_VALUE, SupportPrompt.fromLegacyStage(Int.MAX_VALUE))
    }
}
