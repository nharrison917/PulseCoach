package com.pulsecoach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.pulsecoach.model.HrReading
import com.pulsecoach.model.ZoneConfig

/**
 * A live-scrolling HR chart with color-coded zone bands in the background.
 *
 * The chart shows the last [ZoneConfig]-bounded seconds of HR readings.
 * Zone bands (horizontal colored regions) give instant visual context for
 * where the current effort sits.
 *
 * @param hrHistory  Ordered list of readings from oldest to newest.
 * @param zoneConfig Zone threshold configuration (determines band positions).
 */
@Composable
fun LiveHrChart(
    hrHistory: List<HrReading>,
    zoneConfig: ZoneConfig = ZoneConfig.defaults,
    modifier: Modifier = Modifier
) {
    // CartesianChartModelProducer is Vico's data bus. We hold one reference for
    // the lifetime of this composable and push new data into it as readings arrive.
    val modelProducer = remember { CartesianChartModelProducer() }

    // Zone band decorations — horizontal colored boxes between y-axis thresholds.
    // These are computed once from zoneConfig and reused on every redraw.
    val zoneBands = remember(zoneConfig) { buildZoneBands(zoneConfig) }

    // LaunchedEffect re-runs whenever hrHistory changes (every new heartbeat).
    // runTransaction is a suspend function that safely updates the chart data.
    LaunchedEffect(hrHistory) {
        if (hrHistory.size >= 2) {
            modelProducer.runTransaction {
                // lineSeries feeds a single line into the chart.
                // We use the list index as x (time) and bpm as y.
                lineSeries { series(hrHistory.map { it.bpm }) }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),       // y-axis (bpm values)
            bottomAxis = HorizontalAxis.rememberBottom(),   // x-axis (time index)
            decorations = zoneBands,
        ),
        modelProducer = modelProducer,
        // Auto-scroll to the newest data point on the right edge as readings arrive.
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        modifier = modifier,
    )
}

/**
 * Builds the list of [HorizontalBox] decorations — one per HR zone.
 * Each band spans from the zone's lower bpm to its upper bpm on the y-axis.
 */
private fun buildZoneBands(config: ZoneConfig): List<HorizontalBox> {
    // Zone boundaries as (lowerBpm, upperBpm, Compose Color) triples
    val bands = listOf(
        Triple(0,                    config.zone1MaxBpm, Color(0xFF80B4FF)), // Z1 blue
        Triple(config.zone1MaxBpm,   config.zone2MaxBpm, Color(0xFF80E27E)), // Z2 green
        Triple(config.zone2MaxBpm,   config.zone3MaxBpm, Color(0xFFFFD54F)), // Z3 yellow
        Triple(config.zone3MaxBpm,   config.zone4MaxBpm, Color(0xFFFF8A65)), // Z4 orange
        Triple(config.zone4MaxBpm,   220,                Color(0xFFEF5350)), // Z5 red
    )

    return bands.map { (lower, upper, color) ->
        HorizontalBox(
            // y is a lambda receiving the chart's ExtraStore; we ignore it and return
            // the fixed zone range. The Double type matches Vico's y-axis scale.
            y = { lower.toDouble()..upper.toDouble() },
            // Fill wraps an ARGB int. Alpha 0x33 (20%) keeps zone colors subtle so
            // the HR line stays legible on top of them.
            box = ShapeComponent(fill = Fill(color.copy(alpha = 0.20f).toArgb())),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewChart() {
    // Simulate 30 seconds of tempo-zone data for the preview
    val fakeHistory = (0 until 30).map { i ->
        HrReading(
            bpm = 140 + (i % 8) - 3, // gentle variation around 140
            timestampMs = i * 1000L,
            contactOk = true
        )
    }
    MaterialTheme {
        LiveHrChart(
            hrHistory = fakeHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}
