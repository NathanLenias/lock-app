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
    fun `classic bar is read from its text`() {
        assertEquals("m.youtube.com/shorts", addressBarContent("m.youtube.com/shorts", null))
        assertEquals("youtube.com", addressBarContent("youtube.com", "Search or type web address"))
    }

    @Test
    fun `compose bar is read from the first word of its description`() {
        // Verbatim shape of Firefox 160's ADDRESSBAR_URL_BOX, French and English hints.
        assertEquals(
            "fr.wikipedia.org/wiki/Wikipédia:Accueil_principal",
            addressBarContent("", " fr.wikipedia.org/wiki/Wikipédia:Accueil_principal. Recherche ou adresse"),
        )
        assertEquals("youtube.com", addressBarContent(null, " youtube.com. Search or enter address"))
        assertNull(addressBarContent(null, null))
        assertNull(addressBarContent("", "   "))
    }

    @Test
    fun `compose bar description leads to the same decision as a classic bar`() {
        fun decide(description: String) =
            blockedHostFromAddressBar(addressBarContent("", description), false, blocked)

        assertEquals("m.youtube.com", decide(" m.youtube.com/watch?v=1. Recherche ou adresse"))
        assertEquals("youtube.com", decide(" youtube.com. Search or enter address"))
        assertEquals("youtube.com", decide(" youtube.com:8443. Search or enter address"))
        assertNull(decide(" fr.wikipedia.org/wiki/YouTube. Recherche ou adresse"))
        // Empty tab: only the hint is left, which is not an address.
        assertNull(decide("Recherche ou adresse"))
    }

    @Test
    fun `every supported browser has at least one well-formed address bar id`() {
        assertTrue(BrowserUrlBars.packages.isNotEmpty())
        BrowserUrlBars.packages.forEach { pkg ->
            val ids = BrowserUrlBars.idsFor(pkg)
            assertTrue(pkg, ids.isNotEmpty())
            ids.forEach { id ->
                if (BrowserUrlBars.isComposeTag(id)) {
                    assertTrue("$pkg $id", id.isNotBlank() && id.none { it.isWhitespace() || it == ':' })
                } else {
                    assertTrue("$pkg $id", Regex("[\\w.]+:id/\\w+").matches(id))
                }
            }
        }
    }
}
