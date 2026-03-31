package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.ZoneConfig
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.repository.ZoneConfigRepository
import com.pulsecoach.util.ZoneCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 *
 * Loads the saved zone configuration from Room and exposes it as a [StateFlow].
 * Accepts save/reset commands from the UI and delegates persistence to the repository.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ZoneConfigRepository(
        PulseCoachDatabase.getInstance(application).zoneConfigDao()
    )
    private val profileRepository = UserProfileRepository(application)

    /**
     * The persisted zone configuration, observed as a StateFlow.
     *
     * stateIn() converts the repository's cold Flow into a hot StateFlow:
     * - SharingStarted.WhileSubscribed(5000) keeps the query running for 5 seconds
     *   after the last subscriber disappears (handles screen rotation gracefully).
     * - initialValue = ZoneConfig.defaults is shown while the database loads.
     */
    val savedZoneConfig: StateFlow<ZoneConfig> = repository.zoneConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ZoneConfig.defaults
        )

    /** Persists the given configuration. Called when the user taps Save. */
    fun saveZoneConfig(config: ZoneConfig) {
        viewModelScope.launch {
            repository.saveZoneConfig(config)
        }
    }

    /** Resets to factory defaults. */
    fun resetToDefaults() {
        viewModelScope.launch {
            repository.saveZoneConfig(ZoneConfig.defaults)
        }
    }

    /**
     * Returns true if the user's profile has both restingHr and maxHr set.
     * The Settings screen uses this to enable/disable the Karvonen checkbox.
     */
    val karvonenInputsAvailable: Boolean
        get() {
            val profile = profileRepository.getProfile() ?: return false
            return profile.restingHr != null && profile.maxHr != null
        }

    /**
     * Computes Karvonen zone thresholds from the saved profile, or null if inputs are missing.
     * Called when the user checks "Use Karvonen Formula" — result is snapped into the draft sliders.
     */
    fun karvonenZonesOrNull(): ZoneConfig? {
        val profile = profileRepository.getProfile() ?: return null
        val resting = profile.restingHr ?: return null
        val max = profile.maxHr ?: return null
        return ZoneCalculator.karvonenZones(resting, max)
    }
}
