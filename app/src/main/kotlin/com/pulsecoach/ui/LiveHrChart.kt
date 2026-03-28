package com.pulsecoach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    // X-axis formatter: tick marks appear every 30s (controlled by itemPlacer below).
    // Only show a label at full-minute positions (multiples of 60); return "" at 30s ticks.
    // value.toInt() is the second index within the 5-minute window.
    val minuteFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val second = value.toInt()
            if (second > 0 && second % 60 == 0) "${second / 60}m" else ""
        }
    }

    LaunchedEffect(hrHistory) {
        if (hrHistory.size >= 2) {
            modelProducer.runTransaction {
                // Pass explicit x values (0, 1, 2, …) so each reading maps to its
                // second index within the 300-second window. Combined with the fixed
                // x range below, this anchors the line to the left edge and leaves
                // empty space on the right for future data.
                val xValues = hrHistory.indices.map { it.toDouble() }
                lineSeries {
                    series(x = xValues, y = hrHistory.map { it.bpm.toDouble() })
                }
            }
        }
    }

    // Explicit label component with a known-good ARGB color.
    // Vico's default TextComponent color may not resolve correctly inside MaterialTheme
    // without a custom color scheme, rendering invisible against the white background.
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisLabel = remember(onSurface) { TextComponent(color = onSurface) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                // minX/maxX pin the x-axis to the full 5-minute window regardless of
                // how much data has arrived. minY = 50 gives a stable floor.
                rangeProvider = CartesianLayerRangeProvider.fixed(
                    minX = 0.0,
                    maxX = WINDOW_SECONDS.toDouble(),
                    minY = 50.0
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
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = 30) },
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
