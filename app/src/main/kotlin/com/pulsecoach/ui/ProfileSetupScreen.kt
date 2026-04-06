package com.pulsecoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.viewmodel.ProfileViewModel

/**
 * Screen for entering user profile (age, weight, sex) required by the Keytel calorie formula.
 *
 * On first launch this is the start destination. The back button is hidden so the user
 * can't skip setup. After saving, [onProfileSaved] is called and the caller navigates
 * to the main session screen (popping this screen off the back stack so back never returns here).
 *
 * The same screen is reachable from Settings to edit an existing profile.
 * In that case, [onNavigateBack] is non-null and the back arrow is shown.
 *
 * @param onProfileSaved   Called after a successful save.
 * @param onNavigateBack   If non-null, shown as a back arrow in the top bar (edit mode).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileSaved: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val age by viewModel.age.collectAsStateWithLifecycle()
    val weightKg by viewModel.weightKg.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val restingHr by viewModel.restingHr.collectAsStateWithLifecycle()
    val maxHr by viewModel.maxHr.collectAsStateWithLifecycle()
    val useLbs by viewModel.useLbs.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()

    // Only show the validation error message after the user first tries to save
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Profile") },
                navigationIcon = {
                    // No back arrow on first launch — the user must complete setup
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Lock notice — shown when a live session is active
            if (isRecording) {
                Text(
                    "A session is in progress — profile editing is locked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "PulseCoach uses your age, weight, and biological sex to calculate " +
                    "calories per minute using the Keytel (2005) formula. " +
                    "These values are stored only on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Age input — digits only, range 10–100
            OutlinedTextField(
                value = age,
                onValueChange = { viewModel.onAgeChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Age") },
                suffix = { Text("years") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isRecording,
                modifier = Modifier.fillMaxWidth(),
                isError = showError && age.toIntOrNull()?.let { it !in 10..100 } != false,
                supportingText = if (showError && age.toIntOrNull()?.let { it !in 10..100 } != false) {
                    { Text("Enter a value between 10 and 100") }
                } else null
            )

            // Weight unit toggle — placed above the weight field
            Column {
                Text(
                    "Weight unit",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !useLbs,
                            onClick = { viewModel.setUseLbs(false) },
                            enabled = !isRecording
                        )
                        Text("kg", modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = useLbs,
                            onClick = { viewModel.setUseLbs(true) },
                            enabled = !isRecording
                        )
                        Text("lbs", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // Weight input — range 30–250 kg (66–551 lbs)
            val weightUnit = if (useLbs) "lbs" else "kg"
            val weightRange = if (useLbs) "66 to 551 lbs" else "30 to 250 kg"
            val weightValid = weightKg.toFloatOrNull()?.let { display ->
                val kg = if (useLbs) display / 2.20462f else display
                kg in 30f..250f
            } != false
            OutlinedTextField(
                value = weightKg,
                onValueChange = { input ->
                    // Allow digits and at most one decimal point
                    val cleaned = input.filter { it.isDigit() || it == '.' }
                    val dotCount = cleaned.count { it == '.' }
                    if (dotCount <= 1) viewModel.onWeightChange(cleaned)
                },
                label = { Text("Weight") },
                suffix = { Text(weightUnit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !isRecording,
                modifier = Modifier.fillMaxWidth(),
                isError = showError && !weightValid,
                supportingText = if (showError && !weightValid) {
                    { Text("Enter a value between $weightRange") }
                } else null
            )

            // Biological sex — two radio buttons
            Column {
                Text(
                    "Biological sex",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Determines which Keytel coefficient set to use",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BiologicalSex.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = sex == option,
                                onClick = { viewModel.onSexChange(option) },
                                enabled = !isRecording
                            )
                            Text(
                                // "MALE" -> "Male"
                                text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // --- Optional: HR data for Karvonen zone auto-calculation ---
            Column {
                Text(
                    "HR Zone Data (optional)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Used in Settings to auto-calculate zone thresholds via the Karvonen formula. " +
                        "Leave blank to set zones manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = restingHr,
                        onValueChange = { viewModel.onRestingHrChange(it.filter { c -> c.isDigit() }) },
                        label = { Text("Resting HR") },
                        suffix = { Text("bpm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxHr,
                        onValueChange = { viewModel.onMaxHrChange(it.filter { c -> c.isDigit() }) },
                        label = { Text("Max HR") },
                        suffix = { Text("bpm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    if (viewModel.saveProfile()) {
                        onProfileSaved()
                    } else {
                        showError = true
                    }
                },
                enabled = !isRecording,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
