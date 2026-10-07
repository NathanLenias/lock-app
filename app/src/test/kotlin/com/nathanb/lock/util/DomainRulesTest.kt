package com.nathanb.lock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {

    @Test
    fun `bare domain is kept`() {
        assertEquals("youtube.com", DomainRules.normalize("youtube.com"))
    }

    @Test
    fun `full url is reduced to its host`() {
        assertEquals("youtube.com", DomainRules.normalize("https://www.YouTube.com/watch?v=abc#t=1"))
        assertEquals("reddit.com", DomainRules.normalize("http://reddit.com:443/r/android"))
        assertEquals("lemonde.fr", DomainRules.normalize("  lemonde.fr/  "))
    }

    @Test
    fun `subdomains other than www are kept`() {
        assertEquals("m.youtube.com", DomainRules.normalize("m.youtube.com/shorts/x"))
    }

    @Test
    fun `address bar without scheme is understood`() {
        assertEquals("youtube.com", DomainRules.normalize("youtube.com/watch?v=abc"))
    }

    @Test
    fun `non addresses are rejected`() {
        assertNull(DomainRules.normalize("bonjour"))
        assertNull(DomainRules.normalize("youtube music"))
        assertNull(DomainRules.normalize(""))
        assertNull(DomainRules.normalize("192.168.1.1"))
        assertNull(DomainRules.normalize("-bad.com"))
        assertNull(DomainRules.normalize("youtube."))
        assertNull(DomainRules.normalize("chrome://newtab"))
        assertNull(DomainRules.normalize("about:blank"))
    }

    @Test
    fun `domain and its subdomains match`() {
        val domains = setOf("youtube.com")
        assertTrue(DomainRules.matches("youtube.com", domains))
        assertTrue(DomainRules.matches("m.youtube.com", domains))
        assertTrue(DomainRules.matches("music.youtube.com", domains))
    }

    @Test
    fun `lookalike hosts do not match`() {
        val domains = setOf("youtube.com")
        assertFalse(DomainRules.matches("notyoutube.com", domains))
        assertFalse(DomainRules.matches("youtube.com.evil.net", domains))
        assertFalse(DomainRules.matches("google.com", emptySet()))
    }
}
