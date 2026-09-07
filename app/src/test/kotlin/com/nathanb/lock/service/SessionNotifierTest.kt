package com.nathanb.lock.service

import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.LockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The timeout decision used to live inside the foreground service; it is now a pure
 * function so the rules that bound a session can be checked without Android.
 */
class SessionNotifierTest {

    private val start = 1_000_000L
    private val fiveHours = 5 * 60 * 60 * 1000L

    @Test
    fun `standard session is bounded by the global safety timeout`() {
        val state = LockState(isLocked = true, sessionStartTime = start)
        val plan = SessionNotifier.timeoutPlan(state, fiveHours)
        assertEquals(SessionNotifier.TimeoutPlan(start + fiveHours, EndReason.TIMEOUT.value), plan)
    }

    @Test
    fun `timed session uses its own duration and ends with DURATION`() {
        val duration = 25 * 60 * 1000L
        val state = LockState(isLocked = true, sessionStartTime = start, lockDurationMs = duration, isNoEscape = true)
        val plan = SessionNotifier.timeoutPlan(state, fiveHours)
        assertEquals(SessionNotifier.TimeoutPlan(start + duration, EndReason.DURATION.value), plan)
    }

    @Test
    fun `unlimited global timeout arms nothing`() {
        val state = LockState(isLocked = true, sessionStartTime = start)
        assertNull(SessionNotifier.timeoutPlan(state, 0L))
    }

    @Test
    fun `scheduled window without a duration is bounded by the window end, not the timeout`() {
        val state = LockState(isLocked = true, sessionStartTime = start, isScheduleOrigin = true)
        assertNull(SessionNotifier.timeoutPlan(state, fiveHours))
    }

    @Test
    fun `scheduled session with a duration keeps its duration`() {
        val duration = 10 * 60 * 1000L
        val state = LockState(isLocked = true, sessionStartTime = start, isScheduleOrigin = true, lockDurationMs = duration)
        val plan = SessionNotifier.timeoutPlan(state, fiveHours)
        assertEquals(SessionNotifier.TimeoutPlan(start + duration, EndReason.DURATION.value), plan)
    }

    @Test
    fun `no alarm when idle or without a start time`() {
        assertNull(SessionNotifier.timeoutPlan(LockState(isLocked = false, sessionStartTime = start), fiveHours))
        assertNull(SessionNotifier.timeoutPlan(LockState(isLocked = true, sessionStartTime = null), fiveHours))
    }
}
