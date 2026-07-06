package com.nathanb.lock.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTypeTest {
    @Test
    fun `fromValue maps known values`() {
        assertEquals(ProfileType.STANDARD, ProfileType.fromValue("standard"))
        assertEquals(ProfileType.NO_ESCAPE, ProfileType.fromValue("no_escape"))
    }

    @Test
    fun `fromValue falls back to STANDARD for unknown or null`() {
        assertEquals(ProfileType.STANDARD, ProfileType.fromValue(null))
        assertEquals(ProfileType.STANDARD, ProfileType.fromValue("garbage"))
    }
}
