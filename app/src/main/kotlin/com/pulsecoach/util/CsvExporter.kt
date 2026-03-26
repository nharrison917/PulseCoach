package com.pulsecoach.util

import com.pulsecoach.model.HrSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds CSV content from a list of HR samples.
 *
 * Pure functions — no Android dependencies — so this can be unit tested
 * without a device or emulator.
 */
object CsvExporter {

    /**
     * Builds a CSV string from the given samples.
     * Header row matches the format specified in CLAUDE.md:
     * timestamp_ms, bpm, zone, cal_per_min, cumulative_cal
     */
    fun buildCsv(samples: List<HrSample>): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp_ms,bpm,zone,cal_per_min,cumulative_cal")
        for (sample in samples) {
            // %.4f gives 4 decimal places for calorie values — more than enough precision
            sb.appendLine(
                "${sample.timestampMs},${sample.bpm},${sample.zone}," +
                "%.4f,%.4f".format(sample.calPerMinute, sample.cumulativeCalories)
            )
        }
        return sb.toString()
    }

    /**
     * Returns the filename for a session export.
     * Format: pulsecoach_YYYYMMDD_HHMMSS.csv, based on [startTimeMs].
     */
    fun fileName(startTimeMs: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "pulsecoach_${formatter.format(Date(startTimeMs))}.csv"
    }
}
