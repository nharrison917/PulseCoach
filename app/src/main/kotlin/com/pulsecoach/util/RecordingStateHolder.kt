package com.pulsecoach.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Module-level singleton that broadcasts whether a session is actively recording.
 *
 * Written by LiveSessionViewModel on start/stop; read by SettingsViewModel and
 * ProfileViewModel to disable editing during a live session. Using a Kotlin object
 * avoids any DI wiring — appropriate for a single-user offline app.
 */
object RecordingStateHolder {
    val isRecording: MutableStateFlow<Boolean> = MutableStateFlow(false)
}
