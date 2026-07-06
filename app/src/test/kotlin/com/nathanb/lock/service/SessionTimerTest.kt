package com.nathanb.lock.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimerTest {
    @Test
    fun `remaining is full duration at start`() {
        assertEquals(600_000L, LockForegroundService.remainingMs(600_000L, 1_000_000L, 1_000_000L))
    }

    @Test
    fun `remaining subtracts elapsed time`() {
        // started at t=1_000_000, now t=1_100_000 -> 100s elapsed
        assertEquals(500_000L, LockForegroundService.remainingMs(600_000L, 1_000_000L, 1_100_000L))
    }

    @Test
    fun `remaining is clamped to zero when overdue`() {
        assertEquals(0L, LockForegroundService.remainingMs(600_000L, 1_000_000L, 9_000_000L))
    }
}
