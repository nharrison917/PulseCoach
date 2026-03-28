package com.pulsecoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.viewmodel.EvaluationResult
import com.pulsecoach.viewmodel.EvaluationViewModel
import com.pulsecoach.viewmodel.TestCurveData
import com.pulsecoach.viewmodel.WindowMetrics

/**
 * Debug-only screen that evaluates projection accuracy against 10 held-out
 * synthetic sessions. Three panels: overlay chart, accuracy vs window, 3a vs 3b.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    onNavigateBack: () -> Unit,
    viewModel: EvaluationViewModel = viewModel()
) {
    val result   by viewModel.result.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projection Evaluation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Run button
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.runEvaluation() },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Running...")
                    } else {
                        Text(if (result == null) "Run Evaluation" else "Re-run Evaluation")
                    }
                }
            }

            if (result == null && !isRunning) {
                item {
                    Text(
                        text = "Tap Run to evaluate projection accuracy against 10 held-out synthetic sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    )
                }
            }

            result?.let { evalResult ->
                // ── Panel 1: Overlay chart ──────────────────────────────────────────
                item {
                    SectionHeader("Panel 1 — Projected vs Actual (10-min mark)")
                    Text(
                        text = "Each card: solid = actual calories, faded = polynomial projection " +
                               "from the 10-minute mark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(evalResult.testCurves.take(5)) { curveData ->
                    TestSessionCard(curveData)
                }

                // ── Panel 2: Accuracy vs elapsed time ──────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader("Panel 2 — Accuracy vs Observation Window")
                    Text(
                        text = "Lower MAE / MAPE = better. Accuracy should improve as more " +
                               "of the session is observed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AccuracyTable(
                        label = "Polynomial projection",
                        metrics = evalResult.windowMetrics
                    )
                }

                // ── Panel 3: Phase 3a vs 3b comparison ─────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader("Panel 3 — Polynomial vs Blended (Phase 3a vs 3b)")
                    if (evalResult.blendedWindowMetrics != null) {
                        Text(
                            text = "Blended = 0.4 × polynomial + 0.6 × historical average. " +
                                   "Blended should outperform polynomial-only at shorter windows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ComparisonTable(
                            polyMetrics = evalResult.windowMetrics,
                            blendedMetrics = evalResult.blendedWindowMetrics
                        )
                    } else {
                        Text(
                            text = "Requires 10+ qualifying sessions. Seed and run sessions to unlock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

/** Bold section title with a divider underneath. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
}

/**
 * Card showing actual and projected calorie curves for one test session.
 * Reuses LiveCalorieChart — same component used during live sessions.
 */
@Composable
private fun TestSessionCard(data: TestCurveData) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            LiveCalorieChart(
                actualPoints = data.actual,
                projectedPoints = data.projected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

/**
 * Table with one row per observation window showing MAE and MAPE.
 * Used for the polynomial-only metrics in Panel 2.
 */
@Composable
private fun AccuracyTable(label: String, metrics: List<WindowMetrics>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Header row
            MetricsTableRow(
                window = "Window",
                mae = "MAE (cal)",
                mape = "MAPE %",
                isHeader = true
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            metrics.forEach { m ->
                MetricsTableRow(
                    window = "${m.observationMin} min",
                    mae = "%.1f".format(m.mae),
                    mape = "%.1f%%".format(m.mape)
                )
            }
        }
    }
}

/**
 * Side-by-side comparison of polynomial vs blended MAE and MAPE for Panel 3.
 */
@Composable
private fun ComparisonTable(
    polyMetrics: List<WindowMetrics>,
    blendedMetrics: List<WindowMetrics>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Window",  style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Poly MAE", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Blend MAE", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Poly %",  style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Blend %", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            polyMetrics.zip(blendedMetrics).forEach { (poly, blend) ->
                // Highlight the better MAE value in primary color
                val polyBetter = poly.mae <= blend.mae
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${poly.observationMin} min", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        text = "%.1f".format(poly.mae),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (polyBetter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "%.1f".format(blend.mae),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!polyBetter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Text("%.1f%%".format(poly.mape), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("%.1f%%".format(blend.mape), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** Single data row (or header row) in the accuracy table. */
@Composable
private fun MetricsTableRow(
    window: String,
    mae: String,
    mape: String,
    isHeader: Boolean = false
) {
    val style = if (isHeader) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.bodySmall
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(window, style = style, modifier = Modifier.weight(1.5f))
        Text(mae,    style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(mape,   style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
