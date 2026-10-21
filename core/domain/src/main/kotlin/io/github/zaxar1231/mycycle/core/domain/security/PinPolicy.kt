package io.github.zaxar1231.mycycle.core.domain.security

/** Rules for the app PIN. The PIN only locks the interface; the data itself is encrypted separately. */
object PinPolicy {
    const val LENGTH = 4

    /** Wrong attempts allowed before input is frozen. */
    const val FREE_ATTEMPTS = 5
    const val FIRST_LOCKOUT_MILLIS = 30_000L
    const val MAX_LOCKOUT_MILLIS = 30 * 60_000L

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /**
     * How long input stays frozen after the [failures]-th wrong attempt in a row: nothing for the
     * first [FREE_ATTEMPTS], then 30 s, doubling with every further mistake up to 30 min.
     */
    fun lockoutAfter(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        return (FIRST_LOCKOUT_MILLIS shl doublings).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }

    private const val MAX_DOUBLINGS = 16
}

/** Result of checking an entered PIN. */
sealed interface PinCheck {
    data object Correct : PinCheck

    /** [attemptsLeft] before input is frozen; 0 once it already is. */
    data class Wrong(val attemptsLeft: Int, val lockedUntilMillis: Long?) : PinCheck

    /** Input is frozen; the PIN was not checked. */
    data class LockedOut(val untilMillis: Long) : PinCheck
}

/** Options for locking the app after it goes to the background, in minutes; 0 locks at once. */
object AutoLock {
    val OPTIONS_MINUTES = listOf(0, 1, 5, 15, 30)
    const val DEFAULT_MINUTES = 1
    private const val MILLIS_PER_MINUTE = 60_000L

    /** [backgroundAt] and [now] come from a monotonic clock. A clock that went back locks too. */
    fun shouldLock(backgroundAt: Long, now: Long, lockAfterMinutes: Int): Boolean {
        val away = now - backgroundAt
        return away < 0 || away >= lockAfterMinutes * MILLIS_PER_MINUTE
    }
}
