package com.pulsecoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.model.BleConnectionState
import com.pulsecoach.model.FoundDevice
import com.pulsecoach.model.HrReading
import com.pulsecoach.model.SessionType
import com.pulsecoach.util.ZoneCalculator
import com.pulsecoach.viewmodel.LiveSessionViewModel

/**
 * The main live session screen. Handles the full flow:
 * permission request → BLE scan → device selection → live HR display + recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: LiveSessionViewModel = viewModel()
) {
    val context = LocalContext.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    val connectionState       by viewModel.connectionState.collectAsStateWithLifecycle()
    val foundDevices          by viewModel.foundDevices.collectAsStateWithLifecycle()
    val latestHr              by viewModel.latestHr.collectAsStateWithLifecycle()
    val currentZone           by viewModel.currentZone.collectAsStateWithLifecycle()
    val hrHistory             by viewModel.hrHistory.collectAsStateWithLifecycle()
    val calPerMinute          by viewModel.currentCalPerMinute.collectAsStateWithLifecycle()
    val isRecording           by viewModel.isRecording.collectAsStateWithLifecycle()
    val totalCalories         by viewModel.sessionTotalCalories.collectAsStateWithLifecycle()
    val sessionAvgBpm         by viewModel.sessionAvgBpm.collectAsStateWithLifecycle()
    val sessionAvgCalPerMin   by viewModel.sessionAvgCalPerMinute.collectAsStateWithLifecycle()
    val sessionElapsedSeconds by viewModel.sessionElapsedSeconds.collectAsStateWithLifecycle()
    val targetDuration        by viewModel.targetDurationMinutes.collectAsStateWithLifecycle()
    val actualCalorieCurve       by viewModel.actualCalorieCurve.collectAsStateWithLifecycle()
    val projectedCalorie         by viewModel.projectedCalorieCurve.collectAsStateWithLifecycle()
    val qualifyingSessionCount   by viewModel.qualifyingSessionCount.collectAsStateWithLifecycle()
    val sessionType              by viewModel.sessionType.collectAsStateWithLifecycle()
    val isReconnecting           by viewModel.isReconnecting.collectAsStateWithLifecycle()
    val zoneSeconds              by viewModel.zoneSeconds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PulseCoach") },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!permissionsGranted) {
                PermissionRationaleContent(
                    onGrantClick = { permissionLauncher.launch(requiredPermissions) }
                )
            } else {
                when (val state = connectionState) {
                    is BleConnectionState.Disconnected ->
                        if (isReconnecting) ReconnectingContent(isRecording = isRecording)
                        else DisconnectedContent(onScanClick = viewModel::startScan)

                    is BleConnectionState.Scanning ->
                        ScanningContent(
                            devices = foundDevices,
                            onDeviceClick = viewModel::connectToDevice,
                            onStopClick = viewModel::stopScan
                        )

                    is BleConnectionState.Connecting ->
                        if (isReconnecting) ReconnectingContent(isRecording = isRecording)
                        else ConnectingContent(deviceId = state.deviceId)

                    is BleConnectionState.Connected ->
                        ConnectedContent(
                            hr = latestHr,
                            zone = currentZone,
                            hrHistory = hrHistory,
                            deviceName = state.deviceName,
                            calPerMinute = calPerMinute,
                            isRecording = isRecording,
                            sessionTotalCalories = totalCalories,
                            sessionAvgBpm = sessionAvgBpm,
                            sessionAvgCalPerMinute = sessionAvgCalPerMin,
                            sessionElapsedSeconds = sessionElapsedSeconds,
                            targetDurationMinutes = targetDuration,
                            actualCalorieCurve = actualCalorieCurve,
                            projectedCalorieCurve = projectedCalorie,
                            isBlended = qualifyingSessionCount >= 10,
                            sessionType = sessionType,
                            zoneSeconds = zoneSeconds,
                            onTargetDurationChange = viewModel::setTargetDuration,
                            onSessionTypeChange = viewModel::setSessionType,
                            onStartRecording = viewModel::startRecording,
                            onStopRecording = viewModel::stopRecording,
                            onDisconnectClick = { viewModel.disconnect(state.deviceId) }
                        )

                    is BleConnectionState.Error ->
                        ErrorContent(
                            message = state.message,
                            onRetryClick = viewModel::startScan
                        )

                    else -> DisconnectedContent(onScanClick = viewModel::startScan)
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun PermissionRationaleContent(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bluetooth Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "PulseCoach needs Bluetooth to connect to your Polar H10 heart rate monitor.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGrantClick) {
            Text("Grant Bluetooth Permission")
        }
    }
}

@Composable
private fun DisconnectedContent(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No Device Connected", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Make sure your Polar H10 is powered on and worn.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScanClick) { Text("Scan for Devices") }
    }
}

@Composable
private fun ScanningContent(
    devices: List<FoundDevice>,
    onDeviceClick: (String) -> Unit,
    onStopClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Scanning for Polar devices...", style = MaterialTheme.typography.bodyLarge)
        }

        if (devices.isEmpty()) {
            Text(
                text = "No devices found yet. Make sure your H10 is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Tap a device to connect:",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceCard(device = device, onClick = { onDeviceClick(device.id) })
                }
            }
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onStopClick, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Stop Scan")
        }
    }
}

@Composable
private fun DeviceCard(device: FoundDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("ID: ${device.id}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectingContent(deviceId: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Connecting to $deviceId...", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Shown when the H10 drops signal mid-session and the app is retrying automatically.
 * If a recording is in progress, a note reassures the user it hasn't been discarded.
 */
@Composable
private fun ReconnectingContent(isRecording: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Signal lost — reconnecting...", style = MaterialTheme.typography.bodyLarge)
        if (isRecording) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Recording is paused. It will resume automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    hr: HrReading?,
    zone: Int,
    hrHistory: List<HrReading>,
    deviceName: String,
    calPerMinute: Float,
    isRecording: Boolean,
    sessionTotalCalories: Float,
    sessionAvgBpm: Float,
    sessionAvgCalPerMinute: Float,
    sessionElapsedSeconds: Int,
    targetDurationMinutes: Int,
    actualCalorieCurve: List<Pair<Float, Float>>,
    projectedCalorieCurve: List<Pair<Float, Float>>?,
    isBlended: Boolean,
    sessionType: SessionType?,
    zoneSeconds: Map<Int, Int>,
    onTargetDurationChange: (Int) -> Unit,
    onSessionTypeChange: (SessionType?) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val zoneColor = ZoneCalculator.colorForZone(zone)
    val zoneName  = ZoneCalculator.nameForZone(zone)

    // verticalScroll allows the content to scroll on small screens. fillMaxWidth (not
    // fillMaxSize) lets the column size to its content rather than filling all height.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        // Stats card — zone accent bar on the left, 3×2 data grid, zone name footer
        StatsCard(
            hr = hr,
            zoneColor = zoneColor,
            zoneName = zoneName,
            calPerMinute = calPerMinute,
            isRecording = isRecording,
            sessionAvgBpm = sessionAvgBpm,
            sessionAvgCalPerMinute = sessionAvgCalPerMinute,
            sessionElapsedSeconds = sessionElapsedSeconds,
            sessionTotalCalories = sessionTotalCalories,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // Live HR chart
        if (hrHistory.size >= 2) {
            LiveHrChart(
                hrHistory = hrHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Zone indicator strip
        ZoneStrip(currentZone = zone, modifier = Modifier.fillMaxWidth().height(48.dp))

        // Zone time summary — only shown while recording so it doesn't linger after session ends
        if (isRecording) {
            ZoneTimeSummary(
                zoneSeconds = zoneSeconds,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Calorie chart — only shown while recording and once 2+ data points exist
        if (isRecording && actualCalorieCurve.size >= 2) {
            LiveCalorieChart(
                actualPoints = actualCalorieCurve,
                projectedPoints = projectedCalorieCurve,
                isBlended = isBlended,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Duration picker and intensity picker — both hidden once recording starts
        if (!isRecording) {
            DurationPicker(
                selectedMinutes = targetDurationMinutes,
                onSelect = onTargetDurationChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            IntensityPicker(
                selectedType = sessionType,
                onSelect = onSessionTypeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Start/Stop recording button — color changes to signal active state
            if (isRecording) {
                Button(
                    onClick = onStopRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF5350)
                    )
                ) {
                    Text("Stop")
                }
            } else {
                Button(onClick = onStartRecording) {
                    Text("Record")
                }
            }

            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDisconnectClick) {
                Text("Disconnect")
            }
        }
    }
}

/**
 * Compact stats card with a zone-colored accent bar on the left edge.
 *
 * The 3×2 grid shows: Current BPM / cal/min, Avg BPM / avg cal/min,
 * Time Elapsed / Total Cal. Stats that only apply during recording show "--"
 * when idle so the layout never shifts.
 *
 * IntrinsicSize.Min on the Row lets the accent bar use fillMaxHeight() to
 * match the Column's natural height — the standard Compose pattern for this.
 */
@Composable
private fun StatsCard(
    hr: HrReading?,
    zoneColor: Color,
    zoneName: String,
    calPerMinute: Float,
    isRecording: Boolean,
    sessionAvgBpm: Float,
    sessionAvgCalPerMinute: Float,
    sessionElapsedSeconds: Int,
    sessionTotalCalories: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Zone color accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(zoneColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val bpmText     = hr?.bpm?.toString() ?: "--"
                val calText     = "%.1f".format(calPerMinute)
                val avgBpmText  = if (isRecording) "${sessionAvgBpm.toInt()}" else "--"
                val avgCalText  = if (isRecording) "%.1f".format(sessionAvgCalPerMinute) else "--"
                val elapsedText = if (isRecording) formatElapsed(sessionElapsedSeconds) else "--"
                val totalCalText = if (isRecording) "%.1f".format(sessionTotalCalories) else "--"

                StatRow(
                    leftLabel = "Current BPM",  leftValue = bpmText,
                    rightLabel = "cal/min",      rightValue = calText
                )
                StatRow(
                    leftLabel = "Avg BPM",       leftValue = avgBpmText,
                    rightLabel = "Avg cal/min",  rightValue = avgCalText
                )
                StatRow(
                    leftLabel = "Time Elapsed",  leftValue = elapsedText,
                    rightLabel = "Total Cal",    rightValue = totalCalText
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Zone name in zone color — carries the zone context without a full banner
                Text(
                    text = zoneName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = zoneColor
                )
            }
        }
    }
}

/** One row of the stats grid — two labeled values side by side. */
@Composable
private fun StatRow(
    leftLabel: String, leftValue: String,
    rightLabel: String, rightValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        StatCell(label = leftLabel, value = leftValue, modifier = Modifier.weight(1f))
        StatCell(label = rightLabel, value = rightValue, modifier = Modifier.weight(1f))
    }
}

/** Label (small, muted) stacked above a value (medium weight). */
@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Three filter chips for selecting target session duration before recording.
 * Only the selected chip is filled; the others are outlined.
 */
@Composable
private fun DurationPicker(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Target:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(20, 30, 45, 60).forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = { Text("${minutes}m") }
            )
        }
    }
}

/**
 * Three filter chips for selecting session intensity before recording starts.
 * Tapping the already-selected chip deselects it (returns to null = no type).
 * Colors match the zone palette: Recovery=blue, Steady=green, Push=orange.
 */
@Composable
private fun IntensityPicker(
    selectedType: SessionType?,
    onSelect: (SessionType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Intensity:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SessionType.entries.forEach { type ->
            val chipColor = when (type) {
                SessionType.RECOVERY -> Color(0xFF80B4FF)  // Zone 1 blue
                SessionType.STEADY   -> Color(0xFF80E27E)  // Zone 2 green
                SessionType.PUSH     -> Color(0xFFFF8A65)  // Zone 4 orange
            }
            val isSelected = selectedType == type
            FilterChip(
                selected = isSelected,
                // Tapping the selected chip again deselects it (null = no intent set)
                onClick = { onSelect(if (isSelected) null else type) },
                label = { Text(type.displayLabel) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor.copy(alpha = 0.25f),
                    selectedLabelColor = chipColor
                )
            )
        }
    }
}

/**
 * Displays elapsed time per HR zone as a proportional bar with labels below.
 * Only rendered while a session is active. Each zone's bar width is proportional
 * to seconds spent in that zone relative to total recorded time.
 */
@Composable
private fun ZoneTimeSummary(
    zoneSeconds: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    // Format seconds as M:SS for concise display
    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    Column(modifier = modifier) {
        // Proportional bar row — each zone gets a width slice based on its share of total time
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            for (zone in 1..5) {
                val seconds = zoneSeconds[zone] ?: 0
                // weight() must be > 0 — Compose throws if weight is exactly 0.
                // Adding 1 to each zone ensures a positive weight always, and the +1 bias
                // becomes negligible as session time accumulates.
                val weight = (seconds + 1).toFloat()
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .background(ZoneCalculator.colorForZone(zone).copy(
                            alpha = if (seconds > 0) 1f else 0.2f
                        ))
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Label row — zone name and elapsed time for each zone
        Row(modifier = Modifier.fillMaxWidth()) {
            for (zone in 1..5) {
                val seconds = zoneSeconds[zone] ?: 0
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Z$zone",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZoneCalculator.colorForZone(zone)
                    )
                    Text(
                        text = formatTime(seconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (seconds > 0) 1f else 0.4f
                        )
                    )
                }
            }
        }
    }
}

/** Five colored boxes representing the five zones; current zone is at full opacity. */
@Composable
private fun ZoneStrip(currentZone: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        for (zone in 1..5) {
            val color = ZoneCalculator.colorForZone(zone)
            val isActive = zone == currentZone
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (isActive) color else color.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Z$zone",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) ZoneCalculator.textColorForZone(zone)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetryClick) { Text("Try Again") }
    }
}

/** Formats a second count as "M:SS" (e.g. 143 → "2:23"). */
private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewDisconnected() {
    MaterialTheme { DisconnectedContent(onScanClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewScanning() {
    MaterialTheme {
        ScanningContent(
            devices = listOf(
                FoundDevice("B5D3A1", "Polar H10 B5D3A1", rssi = -62),
                FoundDevice("C4E2B0", "Polar H10 C4E2B0", rssi = -78)
            ),
            onDeviceClick = {},
            onStopClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewConnectedIdle() {
    val fakeHistory = (0 until 20).map { i ->
        HrReading(bpm = 145 + (i % 6) - 2, timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        ConnectedContent(
            hr = HrReading(bpm = 148, timestampMs = 0L, contactOk = true),
            zone = 3,
            hrHistory = fakeHistory,
            deviceName = "Polar H10 B5D3A1",
            calPerMinute = 9.2f,
            isRecording = false,
            sessionTotalCalories = 0f,
            sessionAvgBpm = 0f,
            sessionAvgCalPerMinute = 0f,
            sessionElapsedSeconds = 0,
            targetDurationMinutes = 45,
            actualCalorieCurve = emptyList(),
            projectedCalorieCurve = null,
            isBlended = false,
            sessionType = SessionType.STEADY,
            zoneSeconds = emptyMap(),
            onTargetDurationChange = {},
            onSessionTypeChange = {},
            onStartRecording = {},
            onStopRecording = {},
            onDisconnectClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewConnectedRecording() {
    val fakeHistory = (0 until 20).map { i ->
        HrReading(bpm = 162 + (i % 5) - 2, timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        ConnectedContent(
            hr = HrReading(bpm = 164, timestampMs = 0L, contactOk = true),
            zone = 4,
            hrHistory = fakeHistory,
            deviceName = "Polar H10 B5D3A1",
            calPerMinute = 11.4f,
            isRecording = true,
            sessionTotalCalories = 87.3f,
            sessionAvgBpm = 161f,
            sessionAvgCalPerMinute = 10.8f,
            sessionElapsedSeconds = 1386,
            targetDurationMinutes = 45,
            actualCalorieCurve = (0..15).map { i -> i.toFloat() to (i * 9f + 0.05f * i * i) },
            projectedCalorieCurve = null,
            isBlended = false,
            sessionType = null,
            zoneSeconds = mapOf(1 to 120, 2 to 480, 3 to 600, 4 to 180, 5 to 6),
            onTargetDurationChange = {},
            onSessionTypeChange = {},
            onStartRecording = {},
            onStopRecording = {},
            onDisconnectClick = {}
        )
    }
}
