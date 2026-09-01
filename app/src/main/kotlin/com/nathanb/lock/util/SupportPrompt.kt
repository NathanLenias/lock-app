package com.nathanb.lock.util

/**
 * Support-card cadence: first at 3 completed sessions, then every 10 (10, 20, 30…).
 * Declining moves to the next multiple of 10 above the current count. Supporting
 * re-shows once 20 sessions later (the curious don't always come back), then the
 * every-10 rhythm resumes.
 */
object SupportPrompt {
    const val FIRST_THRESHOLD = 3

    fun nextAfterDecline(completedSessions: Int): Int = (completedSessions / 10 + 1) * 10

    fun nextAfterSupport(completedSessions: Int): Int = completedSessions + 20

    /** Pre-1.3.0 installs stored a stage index into [3, 10, 20, 30]; map it to a threshold. */
    fun fromLegacyStage(stage: Int?): Int = when (stage) {
        null, 0 -> FIRST_THRESHOLD
        1 -> 10
        2 -> 20
        3 -> 30
        4 -> 40
        // Legacy "supported forever": don't retroactively re-prompt old supporters.
        else -> Int.MAX_VALUE
    }
}
