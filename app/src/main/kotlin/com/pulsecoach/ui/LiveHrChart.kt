package com.pulsecoach.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.pulsecoach.model.HrReading

/**
 * A live-scrolling HR chart showing the last 60 seconds of readings.
 *
 * Zone band decorations (HorizontalBox) were removed — in Vico 2.0.0-beta.3
 * decoration draws are not clipped to the chart canvas, causing bands with bpm
 * boundaries outside the visible y-range to render outside the chart area.
 * Zone context is provided by the ZoneStrip below this chart instead.
 *
 * @param hrHistory Ordered list of readings from oldest to newest.
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

    // X-axis formatter: each index = 1 second, so label as "0s", "10s", etc.
    val secondsFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}s" }
    }

    LaunchedEffect(hrHistory) {
        if (hrHistory.size >= 2) {
            modelProducer.runTransaction {
                lineSeries { series(hrHistory.map { it.bpm }) }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                // minY = 50 gives a stable floor so the line doesn't bounce around
                // at the very bottom of the chart when HR is in the 60s–70s.
                // maxY is left as null (auto) so it scales up naturally at high HR.
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 50.0)
            ),
            startAxis = VerticalAxis.rememberStart(
                // Show bpm values as integers on the y-axis
                valueFormatter = bpmFormatter
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                // Show elapsed seconds on the x-axis (1 index = 1 reading ≈ 1 second)
                valueFormatter = secondsFormatter
            ),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewChart() {
    val fakeHistory = (0 until 40).map { i ->
        HrReading(bpm = 140 + (i % 8) - 3, timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        LiveHrChart(
            hrHistory = fakeHistory,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}
