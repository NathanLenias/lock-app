package com.nathanb.lock.service

/**
 * Counts how many times each blocked app or website was opened during the current session,
 * for the line shown on the block overlay. A session is identified by its start time: the
 * counts start over as soon as it changes.
 *
 * Kept in memory on purpose: it is feedback for the session in progress, not history. If
 * Android restarts the process mid-session, the counts restart from zero.
 */
class BlockAttemptCounter {

    private var sessionStartTime: Long? = null
    private val attempts = HashMap<String, Int>()

    /** Counts one more attempt on [target] and returns its total for this session. */
    fun record(sessionStartTime: Long?, target: String): Int {
        syncSession(sessionStartTime)
        val total = (attempts[target] ?: 0) + 1
        attempts[target] = total
        return total
    }

    /** Attempts on [target] so far this session, without counting a new one. */
    fun current(sessionStartTime: Long?, target: String): Int {
        syncSession(sessionStartTime)
        return attempts[target] ?: 0
    }

    private fun syncSession(startTime: Long?) {
        if (startTime == sessionStartTime) return
        sessionStartTime = startTime
        attempts.clear()
    }
}
