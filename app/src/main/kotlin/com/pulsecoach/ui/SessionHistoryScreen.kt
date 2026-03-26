package com.pulsecoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.model.Session
import com.pulsecoach.viewmodel.ExportResult
import com.pulsecoach.viewmodel.SessionHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen listing all completed and in-progress sessions, newest first.
 * Export is handled internally — the ViewModel writes to MediaStore and
 * reports back via [SessionHistoryViewModel.exportResult].
 *
 * @param onNavigateBack Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionHistoryViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()

    // SnackbarHostState is the bridge between our code and the SnackbarHost in the Scaffold.
    // We hold one instance for the lifetime of this composable.
    val snackbarHostState = remember { SnackbarHostState() }

    // When exportResult changes to a non-null value, show a snackbar and then clear the state.
    // LaunchedEffect re-runs whenever exportResult changes.
    LaunchedEffect(exportResult) {
        val result = exportResult ?: return@LaunchedEffect
        val message = when (result) {
            is ExportResult.Success -> "Saved to Downloads: ${result.fileName}"
            is ExportResult.Error   -> "Export failed: ${result.message}"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.clearExportResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            // SnackbarHost renders snackbars at the bottom of the screen automatically.
            // We supply a custom Snackbar so it inherits Material3 styling.
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

@Composable
private fun SessionCard(session: Session, onExportClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(session.startTimeMs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (session.endTimeMs == null) {
                    Text(
                        text = "In progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
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
                    // Derived stats — no schema change needed
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
                // Export only available for completed sessions — in-progress ones
                // don't have a finalized totalCalories/avgBpm written yet.
                if (session.endTimeMs != null) {
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
 * Returns a placeholder if [endMs] is null (session still in progress).
 */
private fun formatDuration(startMs: Long, endMs: Long?): String {
    if (endMs == null) return "Started — not yet stopped"
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
                SessionCard(session = session, onExportClick = {})
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
    )
)
