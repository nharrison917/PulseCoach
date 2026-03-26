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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import com.pulsecoach.util.ZoneCalculator
import com.pulsecoach.viewmodel.LiveSessionViewModel

/**
 * The main live session screen. Handles the full flow:
 * permission request → BLE scan → device selection → live HR display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: LiveSessionViewModel = viewModel()
) {
    val context = LocalContext.current

    // Determine which permissions we need based on Android version.
    // Android 12+ (API 31+) uses the new BLUETOOTH_SCAN / BLUETOOTH_CONNECT model.
    // Older versions use the legacy BLUETOOTH / BLUETOOTH_ADMIN permissions.
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    // Track whether all permissions are currently granted.
    // "var" + "by remember" = a reactive local variable that triggers recomposition when it changes.
    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // rememberLauncherForActivityResult creates a launcher for the system permission dialog.
    // The lambda runs when the user responds (grant or deny).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    // collectAsStateWithLifecycle is the Compose-safe way to observe a StateFlow.
    // It automatically stops collecting when the screen is off-screen, saving battery.
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val foundDevices    by viewModel.foundDevices.collectAsStateWithLifecycle()
    val latestHr        by viewModel.latestHr.collectAsStateWithLifecycle()
    val currentZone     by viewModel.currentZone.collectAsStateWithLifecycle()
    val hrHistory       by viewModel.hrHistory.collectAsStateWithLifecycle()
    val zoneConfig      by viewModel.zoneConfig.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PulseCoach") },
                actions = {
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
                            zoneConfig = zoneConfig,
                            deviceName = state.deviceName,
                            onDisconnectClick = { viewModel.disconnect(state.deviceId) }
                        )

                    is BleConnectionState.Error ->
                        ErrorContent(
                            message = state.message,
                            onRetryClick = viewModel::startScan
                        )

                    // DevicesFound is not used as a primary navigation state in this flow —
                    // devices accumulate in foundDevices StateFlow while Scanning is active.
                    else -> DisconnectedContent(onScanClick = viewModel::startScan)
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────
// Breaking the screen into small focused functions keeps each one under 30 lines
// and makes each state easy to read and preview independently.

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
        Text(
            text = "No Device Connected",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Make sure your Polar H10 is powered on and worn.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScanClick) {
            Text("Scan for Devices")
        }
    }
}

@Composable
private fun ScanningContent(
    devices: List<FoundDevice>,
    onDeviceClick: (String) -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
    zoneConfig: com.pulsecoach.model.ZoneConfig,
    deviceName: String,
    onDisconnectClick: () -> Unit
) {
    val zoneColor   = ZoneCalculator.colorForZone(zone)
    val textColor   = ZoneCalculator.textColorForZone(zone)
    val zoneName    = ZoneCalculator.nameForZone(zone)

    Column(modifier = Modifier.fillMaxSize()) {

        // Zone color banner — the whole top area pulses with the current zone color
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
                    // Large BPM number — the main thing the user watches during a session
                    Text(
                        text = "${hr.bpm}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
                        ),
                        color = textColor
                    )
                    Text(
                        text = "bpm",
                        style = MaterialTheme.typography.headlineMedium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = zoneName,
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor
                    )
                    // Warn the user if the H10 doesn't have good skin contact
                    if (!hr.contactOk) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "! Check sensor contact",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Live scrolling HR chart — visible once we have at least 2 readings
        if (hrHistory.size >= 2) {
            LiveHrChart(
                hrHistory = hrHistory,
                zoneConfig = zoneConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Zone indicator strip — 5 boxes, current zone highlighted
        ZoneStrip(currentZone = zone, modifier = Modifier.fillMaxWidth().height(48.dp))

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onDisconnectClick) {
                Text("Disconnect")
            }
        }
    }
}

/** Five colored boxes representing the five zones; current zone is slightly taller. */
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
// @Preview functions let Android Studio render the UI without running the app.
// We pass realistic training data — not Lorem Ipsum — per CLAUDE.md.

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
private fun PreviewConnectedTempo() {
    val fakeHistory = (0 until 20).map { i ->
        HrReading(bpm = 145 + (i % 6) - 2, timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        ConnectedContent(
            hr = HrReading(bpm = 148, timestampMs = 0L, contactOk = true),
            zone = 3,
            hrHistory = fakeHistory,
            zoneConfig = com.pulsecoach.model.ZoneConfig.defaults,
            deviceName = "Polar H10 B5D3A1",
            onDisconnectClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewConnectedMax() {
    val fakeHistory = (0 until 20).map { i ->
        HrReading(bpm = 180 + (i % 5), timestampMs = i * 1000L, contactOk = true)
    }
    MaterialTheme {
        ConnectedContent(
            hr = HrReading(bpm = 183, timestampMs = 0L, contactOk = true),
            zone = 5,
            hrHistory = fakeHistory,
            zoneConfig = com.pulsecoach.model.ZoneConfig.defaults,
            deviceName = "Polar H10 B5D3A1",
            onDisconnectClick = {}
        )
    }
}
