package com.nathanb.lock.service

/**
 * Browsers whose address bar Lock can read, with the view ids of that bar.
 * WebsiteBlockerService only listens to these packages: a browser missing here is not
 * covered (accepted, see backlog). Chromium forks often keep Chrome's own id, hence the
 * fallbacks. Ids are not public API and can change with a browser update.
 */
object BrowserUrlBars {

    private const val CHROME_URL_BAR = "com.android.chrome:id/url_bar"

    private fun chromium(pkg: String) = listOf("$pkg:id/url_bar", CHROME_URL_BAR)

    private fun mozilla(pkg: String) = listOf(
        "$pkg:id/mozac_browser_toolbar_origin_view",
        "$pkg:id/mozac_browser_toolbar_url_view",
        "$pkg:id/mozac_browser_toolbar_display_url_view",
    )

    private val urlBarIds: Map<String, List<String>> = buildMap {
        // Chrome and its channels
        put("com.android.chrome", listOf(CHROME_URL_BAR))
        listOf("com.chrome.beta", "com.chrome.dev").forEach { put(it, chromium(it)) }
        // Chromium-based browsers
        listOf(
            "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
            "com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser", "com.ecosia.android",
        ).forEach { put(it, chromium(it)) }
        // Samsung Internet (the beta reports the stable build's ids)
        val samsung = listOf(
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/location_bar",
        )
        listOf("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta").forEach { put(it, samsung) }
        // Firefox family (GeckoView toolbar)
        listOf(
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "org.mozilla.fennec_fdroid", "net.waterfox.android.release",
        ).forEach { put(it, mozilla(it)) }
        put("org.mozilla.focus", listOf("org.mozilla.focus:id/urlInputView"))
        // Opera builds report the main package's ids
        val opera = listOf("com.opera.browser:id/url_field", "com.opera.browser:id/url_bar")
        listOf("com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native").forEach { put(it, opera) }
        // DuckDuckGo
        put("com.duckduckgo.mobile.android", listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"))
    }

    val packages: Set<String> get() = urlBarIds.keys

    fun isSupported(packageName: String): Boolean = packageName in urlBarIds

    fun idsFor(packageName: String): List<String> = urlBarIds[packageName].orEmpty()
}
