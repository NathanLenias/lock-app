package com.nathanb.lock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {

    @Test
    fun `same identifier gives the same fingerprint, another one differs`() {
        assertEquals(DeviceIdentity.hash("9774d56d682e549c"), DeviceIdentity.hash("9774d56d682e549c"))
        assertNotEquals(DeviceIdentity.hash("9774d56d682e549c"), DeviceIdentity.hash("9774d56d682e549d"))
    }

    @Test
    fun `fingerprint is short hex and does not contain the identifier`() {
        val fingerprint = DeviceIdentity.hash("9774d56d682e549c")

        assertEquals(32, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" })
        assertFalse(fingerprint.contains("9774d56d682e549c"))
    }
}
