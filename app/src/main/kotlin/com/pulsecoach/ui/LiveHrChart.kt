package com.pulsecoach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.pulsecoach.model.HrReading

private const val WINDOW_SECONDS = 300 // 5-minute fixed x-axis window

/**
 * A live HR chart showing the most recent 5 minutes of readings in a fixed window.
 *
 * The x-axis always spans 0–300 seconds. Data fills from the left; empty space on the
 * right represents future time within the current 5-minute window. Once 5 minutes of
 * data accumulates, the oldest readings drop off the left (rolling window).
 *
 * X-axis tick marks appear every 30 seconds; minute labels (1m, 2m, …) appear only at
 * full-minute positions so the axis stays readable.
 *
 * Zone band decorations (HorizontalBox) were removed — in Vico 2.0.0-beta.3
 * decoration draws are not clipped to the chart canvas, causing bands with bpm
 * boundaries outside the visible y-range to render outside the chart area.
 * Zone context is provided by the ZoneStrip below this chart instead.
 *
 * @param hrHistory Ordered list of readings from oldest to newest (max 300 entries).
 */
@Composable
fun LiveHrChart(
    hrHistory: List<HrReading>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Y-axis formatter: show bpm as plain integers (e.g. "80", not "80.00")
    val bpmFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}" }
    }

    // X-axis formatter: label slots land every 60s (controlled by itemPlacer below),
    // so this formatter is only called at minute boundaries — no empty-string case needed.
    val minuteFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt() / 60}m" }
    }

    // BEGINNER TRAP: LaunchedEffect(hrHistory) cancels and relaunches the coroutine
    // every second when the list reference changes. Rapid cancellation mid-transaction
    // can corrupt CartesianChartModelProducer's internal state, causing the chart to
    // freeze while data keeps accumulating unseen. Fix: key on the stable modelProducer
    // and use snapshotFlow so a single coroutine processes updates sequentially.
    //
    // IMPORTANT: snapshotFlow only tracks Compose State<T> objects — a plain List
    // parameter is invisible to it (emits once, then stops). rememberUpdatedState wraps
    // the parameter in a State<T> so snapshotFlow can see each new value.
    val currentHistory = rememberUpdatedState(hrHistory)
    LaunchedEffect(modelProducer) {
        snapshotFlow { currentHistory.value }
            .collect { history ->
                if (history.size >= 2) {
                    modelProducer.runTransaction {
                        // Pass explicit x values (0, 1, 2, …) so each reading maps to its
                        // second index within the 300-second window. Combined with the fixed
                        // x range below, this anchors the line to the left edge and leaves
                        // empty space on the right for future data.
                        val xValues = history.indices.map { it.toDouble() }
                        lineSeries {
                            series(x = xValues, y = history.map { it.bpm.toDouble() })
                        }
                    }
                }
            }
    }

    // Explicit label component with a known-good ARGB color.
    // Vico's default TextComponent color may not resolve correctly inside MaterialTheme
    // without a custom color scheme, rendering invisible against the white background.
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisLabel = remember(onSurface) { TextComponent(color = onSurface) }

    // Dynamic y-range: zoom to the actual data window ±10 bpm once enough readings
    // exist, so steady-state workouts aren't squashed against a fixed 50 bpm floor.
    // Falls back to minY = 50 / auto-top when fewer than 10 readings are available.
    val (dynamicMinY, dynamicMaxY) = if (hrHistory.size >= 10) {
        val minBpm = hrHistory.minOf { it.bpm }
        val maxBpm = hrHistory.maxOf { it.bpm }
        (minBpm - 10).toDouble() to (maxBpm + 10).toDouble()
    } else {
        50.0 to null  // null maxY lets Vico auto-scale the top
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                // minX/maxX pin the x-axis to the full 5-minute window regardless of
                // how much data has arrived. minY/maxY track the live data range.
                rangeProvider = CartesianLayerRangeProvider.fixed(
                    minX = 0.0,
                    maxX = WINDOW_SECONDS.toDouble(),
                    minY = dynamicMinY,
                    maxY = dynamicMaxY
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                valueFormatter = bpmFormatter
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = minuteFormatter,
                // spacing = 30 places a tick mark (and a label slot) every 30 seconds.
                // The formatter above returns "" at non-minute positions, so only minute
                // labels appear as text while all 30s ticks still get a tick line.
                // spacing = 60 places label slots only at full-minute x values (0, 60, 120, …).
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = 60) },
            ),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewChart() {
    // 150 readings = 2.5 minutes of data, showing empty space on the right
    val fakeHistory = (0 until 150).map { i ->
        HrReading(bpm = 140 + (i % 12) - 5, timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        LiveHrChart(
            hrHistory = fakeHistory,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}
