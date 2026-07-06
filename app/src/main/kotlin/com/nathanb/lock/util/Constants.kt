package com.nathanb.lock.util

object Constants {
    /** Packages that must never be blocked */
    val WHITELISTED_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.dialer",
        "com.android.phone",
        "com.nathanb.lock",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.launcher",
        "com.android.launcher3",
    )

    // Default session settings (used as fallbacks when no user preference is saved)
    const val DEFAULT_TIMEOUT_DURATION_MS = 5 * 60 * 60 * 1000L // 5 hours
    const val DEFAULT_EMERGENCY_UNLOCK_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    const val DEFAULT_MAX_EMERGENCY_UNLOCKS = 2
    const val DEFAULT_GRACE_PERIOD_MS = 30 * 1000L // 30 seconds

    /** Curated list of commonly distracting apps, ordered by suggestion priority */
    val CURATED_SUGGESTIONS = listOf(
        "com.zhiliaoapp.musically",   // TikTok
        "com.instagram.android",      // Instagram
        "com.snapchat.android",       // Snapchat
        "com.google.android.youtube", // YouTube
        "com.twitter.android",        // X
        "com.whatsapp",               // WhatsApp
        "com.facebook.katana",        // Facebook
        "com.reddit.frontpage",       // Reddit
        "tv.twitch.android.app",      // Twitch
        "com.linkedin.android",       // LinkedIn
    )

    const val NOTIFICATION_CHANNEL_ID = "lock_session"
    const val NOTIFICATION_ID = 1
    const val FOREGROUND_SERVICE_REQUEST_CODE = 100

    const val PRIVACY_URL = "https://lock-app.fr/privacy"
}
