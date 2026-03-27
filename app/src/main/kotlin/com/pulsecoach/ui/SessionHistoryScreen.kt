package com.pulsecoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.BuildConfig
import com.pulsecoach.model.Session
import com.pulsecoach.viewmodel.ExportResult
import com.pulsecoach.viewmodel.SeedingState
import com.pulsecoach.viewmodel.SessionHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen listing all completed and incomplete sessions, newest first.
 * Long-pressing any card enters multi-select mode; a trash icon in the top bar
 * triggers a confirmation dialog before permanently deleting selected sessions.
 *
 * @param onNavigateBack Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionHistoryViewModel = viewModel()
) {
    val sessions      by viewModel.sessions.collectAsStateWithLifecycle()
    val exportResult  by viewModel.exportResult.collectAsStateWithLifecycle()
    val selectedIds   by viewModel.selectedIds.collectAsStateWithLifecycle()
    val seedingState  by viewModel.seedingState.collectAsStateWithLifecycle()

    // Derived from selectedIds — drives which top bar and card interactions are shown.
    val isInSelectionMode = selectedIds.isNotEmpty()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // While in selection mode, intercept the system back button to clear the selection
    // instead of navigating away. Standard Android pattern for multi-select screens.
    BackHandler(enabled = isInSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(exportResult) {
        val result = exportResult ?: return@LaunchedEffect
        val message = when (result) {
            is ExportResult.Success -> "Saved to Downloads: ${result.fileName}"
            is ExportResult.Error   -> "Export failed: ${result.message}"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.clearExportResult()
    }

    // Show a snackbar when seeding completes or errors; ignore Idle/InProgress
    LaunchedEffect(seedingState) {
        val message = when (val state = seedingState) {
            is SeedingState.Done  -> "${state.count} synthetic sessions seeded"
            is SeedingState.Error -> "Seed failed: ${state.message}"
            else -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        viewModel.clearSeedingState()
    }

    // Confirmation dialog — shown before any data is permanently deleted.
    if (showDeleteConfirmation) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Sessions") },
            text = {
                Text(
                    "Delete $count session${if (count == 1) "" else "s"}? " +
                    "All recorded data will be permanently removed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                // Selection mode: show count and action icons instead of the normal title.
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Session History") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Seed button: only compiled into debug builds
                        if (BuildConfig.DEBUG) {
                            TextButton(
                                onClick = { viewModel.seedSyntheticSessions() },
                                enabled = seedingState !is SeedingState.InProgress
                            ) {
                                Text(
                                    if (seedingState is SeedingState.InProgress) "Seeding..." else "Seed"
                                )
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyHistoryContent(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        isSelected = session.id in selectedIds,
                        isInSelectionMode = isInSelectionMode,
                        onToggleSelect = { viewModel.toggleSelection(session.id) },
                        onExportClick = { viewModel.exportToCsv(session.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyHistoryContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No Sessions Yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect your H10 and tap Record to start logging a session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Card showing session summary. Long-pressing enters selection mode;
 * tapping while in selection mode toggles the card's selected state.
 * The Export button is hidden during selection mode to avoid accidental taps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: Session,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onExportClick: () -> Unit
) {
    // Selected cards get a primary-color border so the selection is immediately obvious.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isInSelectionMode) onToggleSelect() },
                onLongClick = onToggleSelect
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date + optional SYN tag side by side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDateTime(session.startTimeMs),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (session.notes == "synthetic") {
                        // Small teal chip so synthetic sessions are instantly recognisable
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF00897B),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SYN",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                // Sessions without an end time were interrupted (e.g. app crash).
                if (session.endTimeMs == null) {
                    Text(
                        text = "Incomplete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDuration(session.startTimeMs, session.endTimeMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "%.1f cal".format(session.totalCalories),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val durationMinutes = session.endTimeMs?.let {
                        (it - session.startTimeMs) / 60_000f
                    }
                    val avgCalPerMin = if (durationMinutes != null && durationMinutes > 0f) {
                        session.totalCalories / durationMinutes
                    } else null

                    Text(
                        text = buildString {
                            append("avg ${session.avgBpm.toInt()} bpm")
                            if (avgCalPerMin != null) append("  •  avg ${"%.1f".format(avgCalPerMin)} cal/min")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Export is only available for completed sessions and only outside selection mode.
                if (session.endTimeMs != null && !isInSelectionMode) {
                    OutlinedButton(
                        onClick = onExportClick,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Export CSV", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ── Private formatting helpers ─────────────────────────────────────────────────

/** Formats epoch milliseconds as "MMM d, yyyy  •  h:mm a" (e.g. "Mar 26, 2026  •  10:34 AM"). */
private fun formatDateTime(epochMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy  •  h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

/**
 * Returns a human-readable duration string.
 * Returns "Incomplete" if [endMs] is null (session was not stopped cleanly).
 */
private fun formatDuration(startMs: Long, endMs: Long?): String {
    if (endMs == null) return "Incomplete"
    val totalSeconds = ((endMs - startMs) / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes} min ${seconds} sec" else "${seconds} sec"
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryList() {
    MaterialTheme {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(previewSessions) { session ->
                SessionCard(
                    session = session,
                    isSelected = session.id == 2L,
                    isInSelectionMode = true,
                    onToggleSelect = {},
                    onExportClick = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewHistoryEmpty() {
    MaterialTheme { EmptyHistoryContent() }
}

private val previewSessions = listOf(
    Session(
        id = 1,
        startTimeMs      = 1_743_000_000_000L,
        endTimeMs        = 1_743_000_000_000L + 45 * 60 * 1000L,
        targetDurationMs = null,
        totalCalories    = 423.7f,
        avgBpm           = 152f,
        notes            = ""
    ),
    Session(
        id = 2,
        startTimeMs      = 1_742_900_000_000L,
        endTimeMs        = 1_742_900_000_000L + 32 * 60 * 1000L,
        targetDurationMs = null,
        totalCalories    = 281.2f,
        avgBpm           = 141f,
        notes            = ""
    ),
    Session(
        id = 3,
        startTimeMs      = 1_742_800_000_000L,
        endTimeMs        = null,
        targetDurationMs = null,
        totalCalories    = 0f,
        avgBpm           = 0f,
        notes            = ""
    ),
    Session(
        id = 4,
        startTimeMs      = 1_742_700_000_000L,
        endTimeMs        = 1_742_700_000_000L + 35 * 60 * 1000L,
        targetDurationMs = 35 * 60_000L,
        totalCalories    = 312.4f,
        avgBpm           = 147f,
        notes            = "synthetic"
    )
)
