package com.pulsecoach.model

/**
 * A single heart rate measurement from the Polar H10.
 * The H10 emits one reading per second.
 *
 * @param bpm         Beats per minute.
 * @param timestampMs System time when the reading was received (milliseconds since epoch).
 * @param contactOk   True if the sensor has good skin contact. False means the reading
 *                    may be unreliable — display a warning in the UI.
 */
data class HrReading(
    val bpm: Int,
    val timestampMs: Long,
    val contactOk: Boolean
)
