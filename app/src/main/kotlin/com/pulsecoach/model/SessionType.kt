package com.pulsecoach.model

/**
 * User-declared intensity intent for a session.
 *
 * Stored as the enum name string in Room (e.g. "RECOVERY").
 * Null in the database means the session was not classified — displayed as "--".
 *
 * Three levels cover the practical range of aerobic training:
 *   RECOVERY — easy, low-HR, deliberate rest day effort
 *   STEADY   — comfortable aerobic work, the majority of training volume
 *   PUSH     — hard effort, lactate threshold or above
 */
enum class SessionType(val displayLabel: String) {
    RECOVERY("Recovery"),
    STEADY("Steady"),
    PUSH("Push");

    companion object {
        /**
         * Safely parses a stored string back to a SessionType.
         * Returns null for null input or any unrecognised string,
         * so old database rows never cause a crash.
         */
        fun fromString(value: String?): SessionType? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
