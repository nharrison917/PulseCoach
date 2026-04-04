package com.pulsecoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.pulsecoach.ui.theme.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.model.ZoneConfig
import com.pulsecoach.util.ZoneCalculator
import com.pulsecoach.viewmodel.SettingsViewModel

private const val BPM_MIN = 60
private const val BPM_MAX = 210
private const val ZONE_GAP = 5 // minimum bpm separation between zone boundaries

/**
 * Settings screen for configuring HR zone thresholds.
 *
 * Sliders adjust draft values locally. Changes are only written to Room when
 * the user taps Save, so accidental adjustments don't affect the live session.
 *
 * @param onNavigateBack       Called when the user taps the back arrow.
 * @param onNavigateToProfile  Called when the user taps "Edit Profile".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val saved by viewModel.savedZoneConfig.collectAsStateWithLifecycle()

    // Draft state — local copies the user edits before committing to Room.
    // We use Int (not Float) because bpm is discrete.
    var draft1 by remember { mutableIntStateOf(saved.zone1MaxBpm) }
    var draft2 by remember { mutableIntStateOf(saved.zone2MaxBpm) }
    var draft3 by remember { mutableIntStateOf(saved.zone3MaxBpm) }
    var draft4 by remember { mutableIntStateOf(saved.zone4MaxBpm) }

    // When the saved config changes (e.g. reset to defaults), sync the draft.
    LaunchedEffect(saved) {
        draft1 = saved.zone1MaxBpm
        draft2 = saved.zone2MaxBpm
        draft3 = saved.zone3MaxBpm
        draft4 = saved.zone4MaxBpm
    }

    // Not persisted — acts as a "calculate now" trigger. Checked = formula controls the sliders.
    var useKarvonen by remember { mutableStateOf(false) }
    // Read once on composition; changes to profile require navigating away and back.
    val karvonenAvailable = remember { viewModel.karvonenInputsAvailable }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Adjust the upper bpm boundary for each zone. Zone 5 is everything above Zone 4.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Karvonen checkbox — only enabled when profile has restingHr + maxHr set
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = useKarvonen,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Snap slider drafts to formula values when the box is checked
                            val zones = viewModel.karvonenZonesOrNull()
                            if (zones != null) {
                                draft1 = zones.zone1MaxBpm
                                draft2 = zones.zone2MaxBpm
                                draft3 = zones.zone3MaxBpm
                                draft4 = zones.zone4MaxBpm
                                useKarvonen = true
                            }
                        } else {
                            useKarvonen = false
                        }
                    },
                    enabled = karvonenAvailable
                )
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Use Karvonen Formula", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (karvonenAvailable) "Auto-calculate zones from your resting and max HR"
                        else "Set resting HR and max HR in your profile first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Zone preview strip — updates live as sliders move
            ZonePreviewStrip(
                zoneConfig = ZoneConfig(draft1, draft2, draft3, draft4)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Zone 1 — lower bound is BPM_MIN, upper bound must stay below Zone 2
            ZoneSlider(
                zone = 1,
                label = "Zone 1 — Recovery",
                description = "below ${draft1 + 1} bpm",
                value = draft1,
                valueRange = BPM_MIN..(draft2 - ZONE_GAP),
                formulaControlled = useKarvonen,
                onValueChange = { useKarvonen = false; draft1 = it }
            )

            ZoneSlider(
                zone = 2,
                label = "Zone 2 — Aerobic",
                description = "${draft1 + 1}–$draft2 bpm",
                value = draft2,
                valueRange = (draft1 + ZONE_GAP)..(draft3 - ZONE_GAP),
                formulaControlled = useKarvonen,
                onValueChange = { useKarvonen = false; draft2 = it }
            )

            ZoneSlider(
                zone = 3,
                label = "Zone 3 — Tempo",
                description = "${draft2 + 1}–$draft3 bpm",
                value = draft3,
                valueRange = (draft2 + ZONE_GAP)..(draft4 - ZONE_GAP),
                formulaControlled = useKarvonen,
                onValueChange = { useKarvonen = false; draft3 = it }
            )

            ZoneSlider(
                zone = 4,
                label = "Zone 4 — Threshold",
                description = "${draft3 + 1}–$draft4 bpm",
                value = draft4,
                valueRange = (draft3 + ZONE_GAP)..(BPM_MAX - ZONE_GAP),
                formulaControlled = useKarvonen,
                onValueChange = { useKarvonen = false; draft4 = it }
            )

            // Zone 5 is implicit — no slider needed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .weight(0.08f)
                        .background(ZoneCalculator.colorForZone(5), MaterialTheme.shapes.small)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Zone 5 — Max", style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    Text("above $draft4 bpm", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("no limit", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(32.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Defaults")
                }
                Button(
                    onClick = {
                        viewModel.saveZoneConfig(ZoneConfig(draft1, draft2, draft3, draft4))
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // User profile is in a separate section so it's easy to find
            Text(
                "User Profile",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Profile (age, weight, sex, resting/max HR)")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ThemeDropdown(
                selectedTheme = currentTheme,
                onThemeSelected = onThemeChange
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A single zone slider row with a color swatch, label, bpm range, and slider. */
@Composable
private fun ZoneSlider(
    zone: Int,
    label: String,
    description: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    formulaControlled: Boolean = false
) {
    // When the formula is in control, dim the slider thumb and track to signal read-only intent.
    // The slider stays interactive — dragging it will clear formulaControlled via onValueChange.
    val sliderColors = if (formulaControlled) {
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    } else {
        SliderDefaults.colors()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .weight(0.08f)
                    .background(ZoneCalculator.colorForZone(zone), MaterialTheme.shapes.small)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Sublabel visible only when the formula is controlling this slider
                if (formulaControlled) {
                    Text(
                        "Karvonen formula",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
            // Current threshold value displayed on the right
            Text(
                text = "$value bpm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        // Slider converts Float internally; we round to Int on change
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = valueRange.last - valueRange.first - 1,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Five colored boxes showing the zone layout at the current draft thresholds. */
@Composable
private fun ZonePreviewStrip(zoneConfig: ZoneConfig) {
    Column {
        Text("Preview", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            // Width of each band is proportional to its bpm range
            val total = (BPM_MAX - BPM_MIN).toFloat()
            val widths = listOf(
                zoneConfig.zone1MaxBpm - BPM_MIN,
                zoneConfig.zone2MaxBpm - zoneConfig.zone1MaxBpm,
                zoneConfig.zone3MaxBpm - zoneConfig.zone2MaxBpm,
                zoneConfig.zone4MaxBpm - zoneConfig.zone3MaxBpm,
                BPM_MAX - zoneConfig.zone4MaxBpm
            )
            widths.forEachIndexed { index, width ->
                Box(
                    modifier = Modifier
                        .weight(width / total)
                        .fillMaxSize()
                        .background(ZoneCalculator.colorForZone(index + 1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Z${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZoneCalculator.textColorForZone(index + 1)
                    )
                }
            }
        }
    }
}

/**
 * Dropdown selector for the three app themes.
 * Uses ExposedDropdownMenuBox so it inherits Material3 styling automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedTheme.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            // MenuAnchorType.PrimaryNotEditable = read-only field that anchors a dropdown
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppTheme.entries.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.displayName) },
                    onClick = {
                        onThemeSelected(theme)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSettings() {
    MaterialTheme {
        // Preview with defaults — can't use the real ViewModel in a @Preview
        Column(modifier = Modifier.padding(16.dp)) {
            ZonePreviewStrip(ZoneConfig.defaults)
            Spacer(Modifier.height(16.dp))
            ZoneSlider(
                zone = 1,
                label = "Zone 1 — Recovery",
                description = "below 115 bpm",
                value = 114,
                valueRange = 60..130,
                onValueChange = {}
            )
        }
    }
}
