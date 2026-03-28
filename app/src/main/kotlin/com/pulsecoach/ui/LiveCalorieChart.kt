package com.pulsecoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * Cumulative calorie chart with an optional projected extension and confidence band.
 *
 * Four visual series (all always present in the model to keep the Vico layer count stable):
 *   0 — Actual (solid primary color): calories accumulated this session.
 *   1 — Projected (45% opacity): polynomial/blend extrapolation to the target duration.
 *        Only meaningful once [projectedPoints] is non-null (after 10 min of data).
 *   2 — Upper confidence band (20% opacity): projected × (1 + σ).
 *   3 — Lower confidence band (20% opacity): projected × (1 − σ).
 *        Series 2 and 3 are dummies when [projectionBand] is null (< 5 past sessions).
 *
 * When there is no real data for a series, a flat 2-point segment at the last actual
 * position is used as a dummy — Vico needs ≥ 2 points to render without warnings.
 *
 * @param actualPoints    (elapsedMinutes, cumulativeCalories) — one per minute.
 * @param projectedPoints Projected curve from [PolynomialProjector.project], or null.
 * @param isBlended       True when the projection is a poly+historical blend.
 * @param projectionBand  Fractional σ of past ratios (e.g. 0.12 = ±12%). Null until ≥5 sessions.
 */
@Composable
fun LiveCalorieChart(
    actualPoints: List<Pair<Float, Float>>,
    projectedPoints: List<Pair<Float, Float>>?,
    isBlended: Boolean = false,
    projectionBand: Float? = null,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val calFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}" }
    }
    val minuteFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}m" }
    }

    val primaryColor   = MaterialTheme.colorScheme.primary
    val projectedColor = primaryColor.copy(alpha = 0.45f)
    // Band lines: same hue at very low opacity so they frame without distracting
    val bandColor      = primaryColor.copy(alpha = 0.20f)

    val onSurface  = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisLabel  = remember(onSurface) { TextComponent(color = onSurface) }

    val actualLine    = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(primaryColor)))
    val projectedLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(projectedColor)))
    val upperBandLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(bandColor)))
    val lowerBandLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(bandColor)))

    // Rebuild the model whenever any input data changes
    LaunchedEffect(actualPoints, projectedPoints, projectionBand) {
        if (actualPoints.size < 2) return@LaunchedEffect

        modelProducer.runTransaction {
            lineSeries {
                // Series 0 — actual calories, x = elapsed minutes
                series(
                    x = actualPoints.map { it.first },
                    y = actualPoints.map { it.second }
                )

                // Dummy used when a series has no real data: a flat 2-point segment
                // at the last actual position — invisible but satisfies Vico's ≥2 rule.
                val dummyX = listOf(actualPoints.last().first, actualPoints.last().first + 1f)
                val dummyY = listOf(actualPoints.last().second, actualPoints.last().second)

                // Series 1 — projected (or dummy)
                val proj = projectedPoints?.takeIf { it.size >= 2 }
                if (proj != null) {
                    series(x = proj.map { it.first }, y = proj.map { it.second })
                } else {
                    series(x = dummyX, y = dummyY)
                }

                // Series 2 & 3 — upper/lower confidence band (or dummies)
                if (proj != null && projectionBand != null) {
                    val upperPts = proj.map { (t, cal) -> t to cal + projectionBand * cal }
                    val lowerPts = proj.map { (t, cal) -> t to (cal - projectionBand * cal).coerceAtLeast(0f) }
                    series(x = upperPts.map { it.first }, y = upperPts.map { it.second })
                    series(x = lowerPts.map { it.first }, y = lowerPts.map { it.second })
                } else {
                    series(x = dummyX, y = dummyY)
                    series(x = dummyX, y = dummyY)
                }
            }
        }
    }

    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        actualLine,
                        projectedLine,
                        upperBandLine,
                        lowerBandLine
                    ),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0)
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = axisLabel,
                    valueFormatter = calFormatter
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = axisLabel,
                    valueFormatter = minuteFormatter
                )
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Caption: projection mode, start minute, and band width when active
        val caption = when {
            projectedPoints == null -> "Projection starts after 10 min"
            projectionBand != null -> {
                val startMinute = actualPoints.last().first.toInt()
                val mode = if (isBlended) "Historical blend" else "Polynomial projection"
                val pct = (projectionBand * 100).toInt()
                "$mode from min $startMinute  \u2022  \u00b1${pct}% historical range"
            }
            else -> {
                val startMinute = actualPoints.last().first.toInt()
                val mode = if (isBlended) "Historical blend" else "Polynomial projection"
                "$mode from min $startMinute"
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewActualOnly() {
    val actual = (0..12).map { i -> i.toFloat() to (i * 8f + 0.05f * i * i) }
    MaterialTheme {
        LiveCalorieChart(
            actualPoints = actual,
            projectedPoints = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewWithProjection() {
    val actual = (0..15).map { i -> i.toFloat() to (i * 8f + 0.05f * i * i) }
    val lastActual = actual.last()
    val projected = (0..30).map { i ->
        (lastActual.first + i) to (lastActual.second + i * 8.2f)
    }
    MaterialTheme {
        LiveCalorieChart(
            actualPoints = actual,
            projectedPoints = projected,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewWithBand() {
    val actual = (0..15).map { i -> i.toFloat() to (i * 8f + 0.05f * i * i) }
    val lastActual = actual.last()
    val projected = (0..30).map { i ->
        (lastActual.first + i) to (lastActual.second + i * 8.2f)
    }
    MaterialTheme {
        LiveCalorieChart(
            actualPoints = actual,
            projectedPoints = projected,
            projectionBand = 0.12f,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}
