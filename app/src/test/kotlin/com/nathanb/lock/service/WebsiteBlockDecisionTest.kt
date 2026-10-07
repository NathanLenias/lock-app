package com.nathanb.lock.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteBlockDecisionTest {

    private val blocked = setOf("youtube.com")

    @Test
    fun `blocked host shown in the bar is returned`() {
        assertEquals("m.youtube.com", blockedHostFromAddressBar("m.youtube.com/watch?v=1", false, blocked))
        assertEquals("youtube.com", blockedHostFromAddressBar("https://www.youtube.com/", false, blocked))
    }

    @Test
    fun `bar being edited is ignored`() {
        assertNull(blockedHostFromAddressBar("youtube.com", true, blocked))
    }

    @Test
    fun `search queries and other sites are ignored`() {
        assertNull(blockedHostFromAddressBar("youtube music", false, blocked))
        assertNull(blockedHostFromAddressBar("lemonde.fr/international", false, blocked))
        assertNull(blockedHostFromAddressBar(null, false, blocked))
        assertNull(blockedHostFromAddressBar("about:blank", false, blocked))
    }

    @Test
    fun `every supported browser has at least one address bar id`() {
        assertTrue(BrowserUrlBars.packages.isNotEmpty())
        BrowserUrlBars.packages.forEach { pkg ->
            assertTrue(pkg, BrowserUrlBars.idsFor(pkg).isNotEmpty())
            assertTrue(pkg, BrowserUrlBars.idsFor(pkg).all { ":id/" in it })
        }
    }
}
