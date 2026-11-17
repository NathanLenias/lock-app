package com.nathanb.lock.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockAttemptCounterTest {

    private val counter = BlockAttemptCounter()

    @Test
    fun `attempts add up per target within a session`() {
        assertEquals(1, counter.record(1_000L, "com.instagram.android"))
        assertEquals(2, counter.record(1_000L, "com.instagram.android"))
        assertEquals(1, counter.record(1_000L, "com.twitter.android"))
        assertEquals(3, counter.record(1_000L, "com.instagram.android"))
    }

    @Test
    fun `apps and websites are counted apart`() {
        counter.record(1_000L, "com.reddit.frontpage")
        assertEquals(1, counter.record(1_000L, "reddit.com"))
    }

    @Test
    fun `a new session starts from zero`() {
        counter.record(1_000L, "com.instagram.android")
        counter.record(1_000L, "com.instagram.android")
        assertEquals(1, counter.record(2_000L, "com.instagram.android"))
    }

    @Test
    fun `reading the count does not add an attempt`() {
        assertEquals(0, counter.current(1_000L, "com.instagram.android"))
        counter.record(1_000L, "com.instagram.android")
        assertEquals(1, counter.current(1_000L, "com.instagram.android"))
        assertEquals(1, counter.current(1_000L, "com.instagram.android"))
        assertEquals(2, counter.record(1_000L, "com.instagram.android"))
    }

    @Test
    fun `reading under a new session forgets the previous one`() {
        counter.record(1_000L, "com.instagram.android")
        assertEquals(0, counter.current(2_000L, "com.instagram.android"))
    }
}
