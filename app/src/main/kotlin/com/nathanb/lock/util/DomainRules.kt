package com.nathanb.lock.util

import java.util.Locale

/**
 * Website rules shared by the input field and the browser address bar reader.
 * Pure Kotlin on purpose: unit-tested on the JVM.
 */
object DomainRules {

    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://")
    private val PORT = Regex(":\\d*$")

    /**
     * Reduces what the user typed or what a browser shows ("https://www.YouTube.com/watch?v=x",
     * "m.youtube.com/shorts") to a bare host ("youtube.com", "m.youtube.com").
     * Returns null when the text is not a website address (a search query, a single word).
     */
    fun normalize(input: String): String? {
        var text = input.trim().lowercase(Locale.ROOT)
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        text = text.replace(SCHEME, "")
        text = text.substringBefore('/').substringBefore('?').substringBefore('#')
        text = text.substringAfterLast('@')
        text = text.replace(PORT, "").trimEnd('.')
        if (text.startsWith("www.")) text = text.removePrefix("www.")
        return text.takeIf { isValidHost(it) }
    }

    /** True when [host] is one of [domains] or a subdomain of one of them. */
    fun matches(host: String, domains: Collection<String>): Boolean =
        domains.any { host == it || host.endsWith(".$it") }

    private fun isValidHost(host: String): Boolean {
        if (host.length > 253) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        val labelsValid = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
        val tld = labels.last()
        return labelsValid && tld.length >= 2 && tld.all { it.isLetter() }
    }
}
