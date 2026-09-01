package com.nathanb.lock.service

import com.nathanb.lock.data.model.LockState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timeout alarm carries the start time of the session it was armed for. A stale
 * alarm (session A ended by NFC, session B started) must never end the wrong session.
 */
class SessionTimeoutReceiverTest {

    private val sessionA = 1_000_000L
    private val sessionB = 2_000_000L

    @Test
    fun `ends the session the alarm was armed for`() {
        val state = LockState(isLocked = true, sessionStartTime = sessionA)
        assertTrue(SessionTimeoutReceiver.matchesSession(state, sessionA))
    }

    @Test
    fun `ignores a stale alarm from a previous session`() {
        val state = LockState(isLocked = true, sessionStartTime = sessionB)
        assertFalse(SessionTimeoutReceiver.matchesSession(state, sessionA))
    }

    @Test
    fun `ignores the alarm when no session is active`() {
        val state = LockState(isLocked = false, sessionStartTime = null)
        assertFalse(SessionTimeoutReceiver.matchesSession(state, sessionA))
    }

    @Test
    fun `ignores the alarm when the active session has no start time`() {
        val state = LockState(isLocked = true, sessionStartTime = null)
        assertFalse(SessionTimeoutReceiver.matchesSession(state, sessionA))
    }
}
