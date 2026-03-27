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
 * Cumulative calorie chart with an optional projected extension.
 *
 * Two visual series:
 *   - Actual (solid primary color): calories accumulated so far this session.
 *   - Projected (lighter color): polynomial extrapolation to the target duration.
 *     Only rendered once [projectedPoints] is non-null (after 10 min of data).
 *
 * Both series always exist in the model so the Vico layer count stays stable.
 * When there is no projection, the second series is a single-point dummy at the
 * last actual position — nothing new is drawn.
 *
 * Vico note: Vico 2.0.0-beta.3 does not expose a native dashed-stroke option on
 * LineCartesianLayer.Line. The projection is instead rendered in a lighter
 * opacity of the primary color, which is visually clear and requires no hacks.
 *
 * @param actualPoints     (elapsedMinutes, cumulativeCalories) — one per minute.
 * @param projectedPoints  Projected curve from [PolynomialProjector.project], or null.
 * @param isBlended        True when the projection is a poly+historical blend (>= 10 sessions).
 *                         Controls the caption label shown below the chart.
 */
@Composable
fun LiveCalorieChart(
    actualPoints: List<Pair<Float, Float>>,
    projectedPoints: List<Pair<Float, Float>>?,
    isBlended: Boolean = false,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val calFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}" }
    }
    val minuteFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}m" }
    }

    // Actual line: solid primary color.
    // Projected line: same hue at 45% opacity — clearly "estimated, not real".
    // fill() is a Vico helper that wraps a Compose Color into a core Fill object.
    val primaryColor    = MaterialTheme.colorScheme.primary
    val projectedColor  = primaryColor.copy(alpha = 0.45f)
    // Same fix as LiveHrChart — explicit ARGB color prevents invisible labels.
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisLabel = remember(onSurface) { TextComponent(color = onSurface) }

    val actualLine    = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(primaryColor))
    )
    val projectedLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(projectedColor))
    )

    // Update the chart model whenever the input data changes
    LaunchedEffect(actualPoints, projectedPoints) {
        if (actualPoints.size < 2) return@LaunchedEffect

        modelProducer.runTransaction {
            lineSeries {
                // Series 0 — actual calories, x = elapsed minutes
                series(
                    x = actualPoints.map { it.first },
                    y = actualPoints.map { it.second }
                )
                // Series 1 — projected, or a 2-point dummy at the last actual position.
                // A 2-point (not 1-point) dummy is used because Vico needs >= 2 points
                // to render a line segment; a 1-point series would log a warning.
                val proj = projectedPoints?.takeIf { it.size >= 2 }
                if (proj != null) {
                    series(
                        x = proj.map { it.first },
                        y = proj.map { it.second }
                    )
                } else {
                    val last = actualPoints.last()
                    series(
                        x = listOf(last.first, last.first + 1f),
                        y = listOf(last.second, last.second)
                    )
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
                        projectedLine
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
            // Chart does not scroll — the x range grows as time passes
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Caption below the chart: shows projection mode and start point when active
        val caption = if (projectedPoints != null) {
            val startMinute = actualPoints.last().first.toInt()
            val mode = if (isBlended) "Historical blend" else "Polynomial projection"
            "$mode from min $startMinute"
        } else {
            "Projection starts after 10 min"
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
