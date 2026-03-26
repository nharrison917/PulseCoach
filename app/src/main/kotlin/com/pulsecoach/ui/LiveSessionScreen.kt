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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.model.BleConnectionState
import com.pulsecoach.model.FoundDevice
import com.pulsecoach.model.HrReading
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

    val connectionState     by viewModel.connectionState.collectAsStateWithLifecycle()
    val foundDevices        by viewModel.foundDevices.collectAsStateWithLifecycle()
    val latestHr            by viewModel.latestHr.collectAsStateWithLifecycle()
    val currentZone         by viewModel.currentZone.collectAsStateWithLifecycle()
    val hrHistory           by viewModel.hrHistory.collectAsStateWithLifecycle()
    val calPerMinute        by viewModel.currentCalPerMinute.collectAsStateWithLifecycle()
    val isRecording         by viewModel.isRecording.collectAsStateWithLifecycle()
    val totalCalories       by viewModel.sessionTotalCalories.collectAsStateWithLifecycle()
    val sessionAvgBpm       by viewModel.sessionAvgBpm.collectAsStateWithLifecycle()
    val sessionAvgCalPerMin by viewModel.sessionAvgCalPerMinute.collectAsStateWithLifecycle()

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
                        DisconnectedContent(onScanClick = viewModel::startScan)

                    is BleConnectionState.Scanning ->
                        ScanningContent(
                            devices = foundDevices,
                            onDeviceClick = viewModel::connectToDevice,
                            onStopClick = viewModel::stopScan
                        )

                    is BleConnectionState.Connecting ->
                        ConnectingContent(deviceId = state.deviceId)

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
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val zoneColor = ZoneCalculator.colorForZone(zone)
    val textColor = ZoneCalculator.textColorForZone(zone)
    val zoneName  = ZoneCalculator.nameForZone(zone)

    Column(modifier = Modifier.fillMaxSize()) {

        // Zone color banner — fills available space above the chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(zoneColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hr == null) {
                    CircularProgressIndicator(color = textColor)
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for HR data...", color = textColor)
                } else {
                    // Fixed sp value — the previous `fontSize * 2` worked but was fragile
                    // (it multiplied a TextUnit by a Float, breaking if the base size changed)
                    Text(
                        text = "${hr.bpm}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 96.sp
                        ),
                        color = textColor
                    )
                    Text(
                        text = "bpm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = zoneName,
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor
                    )
                    Spacer(Modifier.height(8.dp))
                    // Cal/min is always shown once HR is live — gives feedback even before recording.
                    // Shows 0.0 when HR is below 90 bpm (formula unreliable range).
                    Text(
                        text = "%.1f cal/min".format(calPerMinute),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }
            }
        }

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

        // Recording status row — only visible while a session is active
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: red dot + "Recording" label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFEF5350), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Recording",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Right: three live running stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatChip(label = "total", value = "%.1f cal".format(sessionTotalCalories))
                    StatChip(label = "avg bpm", value = "${sessionAvgBpm.toInt()}")
                    StatChip(label = "avg cal/min", value = "%.1f".format(sessionAvgCalPerMinute))
                }
            }
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
                        containerColor = Color(0xFFEF5350) // red while recording
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
 * Compact two-line label used in the recording stats row.
 * Top line is the value (prominent), bottom line is the label (muted).
 */
@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            onStartRecording = {},
            onStopRecording = {},
            onDisconnectClick = {}
        )
    }
}
