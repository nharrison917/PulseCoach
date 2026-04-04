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
 * Cumulative calorie chart with an optional projected extension and endpoint annotation.
 *
 * Two visual series:
 *   0 — Actual (solid primary color): calories accumulated this session.
 *   1 — Projected (45% opacity): polynomial/blend extrapolation to the target duration.
 *        Only meaningful once [projectedPoints] is non-null (after 10 min of data).
 *
 * When [projectionBand] is active, the confidence range is shown as a caption annotation
 * at the target endpoint rather than as band lines on the chart.
 *
 * @param actualPoints          (elapsedMinutes, cumulativeCalories) — one per minute.
 * @param projectedPoints       Projected curve from [PolynomialProjector.project], or null.
 * @param isBlended             True when the projection is a poly+historical blend.
 * @param projectionBand        Fractional σ of past ratios (e.g. 0.12 = ±12%). Null until ≥5 sessions.
 * @param projectedFinalCalories Projected calorie total at the target duration. Used for the endpoint annotation.
 */
@Composable
fun LiveCalorieChart(
    actualPoints: List<Pair<Float, Float>>,
    projectedPoints: List<Pair<Float, Float>>?,
    isBlended: Boolean = false,
    projectionBand: Float? = null,
    projectedFinalCalories: Float? = null,
    isOverTarget: Boolean = false,
    caloriesAtTarget: Float? = null,
    firstProjectedCalories: Float? = null,
    targetDurationMinutes: Int = 0,
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
    // Gray out the projection once the session has passed the target — it becomes historical context.
    val projectedColor = if (isOverTarget) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    } else {
        primaryColor.copy(alpha = 0.45f)
    }

    val onSurface  = MaterialTheme.colorScheme.onSurface.toArgb()
    val axisLabel  = remember(onSurface) { TextComponent(color = onSurface) }

    val actualLine    = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(primaryColor)))
    val projectedLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(projectedColor)))

    // Rebuild the model whenever any input data changes
    LaunchedEffect(actualPoints, projectedPoints) {
        if (actualPoints.size < 2) return@LaunchedEffect

        modelProducer.runTransaction {
            lineSeries {
                // Series 0 — actual calories, x = elapsed minutes
                series(
                    x = actualPoints.map { it.first },
                    y = actualPoints.map { it.second }
                )

                // Series 1 — projected (or a flat dummy when not yet available)
                // Dummy: 2-point flat segment at the last actual position — invisible
                // but required so Vico always sees the same number of series.
                val proj = projectedPoints?.takeIf { it.size >= 2 }
                if (proj != null) {
                    series(x = proj.map { it.first }, y = proj.map { it.second })
                } else {
                    val dummyX = listOf(actualPoints.last().first, actualPoints.last().first + 1f)
                    val dummyY = listOf(actualPoints.last().second, actualPoints.last().second)
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
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Caption: projection mode, start minute, band width, and projected final calorie when available.
        // When the session has passed the target, simplify to a pinned summary line.
        val projectedLabel = projectedFinalCalories?.let { "  \u2022  ~${it.toInt()} cal projected" } ?: ""
        val caption = when {
            isOverTarget -> {
                // Projection has served its purpose — show pinned value or a neutral label.
                if (projectedFinalCalories != null) "~${projectedFinalCalories.toInt()} cal projected at $targetDurationMinutes min"
                else "Session over target"
            }
            projectedPoints == null -> "Projection starts after 10 min"
            projectionBand != null && projectedFinalCalories != null -> {
                // Show the calorie range at the target endpoint instead of band lines.
                val startMinute = actualPoints.last().first.toInt()
                val mode = if (isBlended) "Historical blend" else "Polynomial projection"
                val upperCal = (projectedFinalCalories * (1 + projectionBand)).toInt()
                val lowerCal = (projectedFinalCalories * (1 - projectionBand)).toInt()
                "$mode  \u2022  ${startMinute}-min session  \u2022  ~${lowerCal}\u2013${upperCal} cal at ${targetDurationMinutes}m"
            }
            else -> {
                val startMinute = actualPoints.last().first.toInt()
                val mode = if (isBlended) "Historical blend" else "Polynomial projection"
                "$mode  \u2022  ${startMinute}-min session$projectedLabel"
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        // Second line: actual calories at the target minute, shown only once the session passes target.
        if (caloriesAtTarget != null) {
            Text(
                text = "Actual at $targetDurationMinutes min: ${caloriesAtTarget.toInt()} cal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        // Third line: first projection from min 10, shown after session ends for retrospective comparison.
        if (caloriesAtTarget != null && firstProjectedCalories != null) {
            Text(
                text = "Projected at min 10: ~${firstProjectedCalories.toInt()} cal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
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
