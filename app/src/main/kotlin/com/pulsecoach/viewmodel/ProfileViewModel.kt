package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.UserProfile
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.util.RecordingStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the profile setup screen.
 *
 * Uses AndroidViewModel (instead of plain ViewModel) because it needs Application
 * context to instantiate UserProfileRepository — which reads SharedPreferences.
 * ViewModels must not hold an Activity reference (memory leak risk), but
 * Application context is safe since it lives as long as the app process.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application)

    // Each field is stored as a String so the TextField can hold partial input
    // (e.g. "7" while the user is typing "75") without constant parse errors.
    private val _age = MutableStateFlow("")
    val age: StateFlow<String> = _age.asStateFlow()

    // Display string — holds kg value when useLbs=false, lbs value when useLbs=true.
    // Weight is always stored internally as kg; conversion happens on load and save.
    private val _weightKg = MutableStateFlow("")
    val weightKg: StateFlow<String> = _weightKg.asStateFlow()

    private val _sex = MutableStateFlow(BiologicalSex.MALE)
    val sex: StateFlow<BiologicalSex> = _sex.asStateFlow()

    // Optional — used only for Karvonen zone calculation. Stored as String so
    // the TextField can hold partial input without constant parse errors.
    private val _restingHr = MutableStateFlow("")
    val restingHr: StateFlow<String> = _restingHr.asStateFlow()

    private val _maxHr = MutableStateFlow("")
    val maxHr: StateFlow<String> = _maxHr.asStateFlow()

    private val _useLbs = MutableStateFlow(false)
    val useLbs: StateFlow<Boolean> = _useLbs.asStateFlow()

    /** True while a live session is recording — all profile fields are locked. */
    val isRecording: StateFlow<Boolean> = RecordingStateHolder.isRecording.asStateFlow()

    init {
        // Pre-populate fields if the user is editing an existing profile
        val existing = repository.getProfile()
        if (existing != null) {
            _age.value = existing.age.toString()
            _useLbs.value = existing.useLbs
            // Convert stored kg to the display unit
            _weightKg.value = if (existing.useLbs) {
                "%.1f".format(existing.weightKg * 2.20462f)
            } else {
                existing.weightKg.toString()
            }
            _sex.value = existing.sex
            _restingHr.value = existing.restingHr?.toString() ?: ""
            _maxHr.value = existing.maxHr?.toString() ?: ""
        }
    }

    /** Updates the draft age string as the user types. */
    fun onAgeChange(value: String) { _age.value = value }

    /** Updates the draft weight display string as the user types. */
    fun onWeightChange(value: String) { _weightKg.value = value }

    /** Updates the selected biological sex. */
    fun onSexChange(value: BiologicalSex) { _sex.value = value }

    /** Updates the draft resting HR string as the user types. */
    fun onRestingHrChange(value: String) { _restingHr.value = value }

    /** Updates the draft max HR string as the user types. */
    fun onMaxHrChange(value: String) { _maxHr.value = value }

    /**
     * Switches the weight display unit, converting the current draft value so the
     * number shown in the TextField stays equivalent (e.g. 80 kg -> 176.4 lbs).
     */
    fun setUseLbs(value: Boolean) {
        if (value == _useLbs.value) return
        val current = _weightKg.value.toFloatOrNull()
        if (current != null) {
            _weightKg.value = if (value) {
                // kg -> lbs
                "%.1f".format(current * 2.20462f)
            } else {
                // lbs -> kg
                "%.1f".format(current / 2.20462f)
            }
        }
        _useLbs.value = value
    }

    /**
     * Validates required fields and saves the profile.
     * restingHr and maxHr are optional — blank input is saved as null.
     * Weight is always saved as kg regardless of the display unit.
     * @return true if save succeeded, false if any required field is out of range.
     */
    fun saveProfile(): Boolean {
        val age = _age.value.toIntOrNull() ?: return false
        val weightDisplay = _weightKg.value.toFloatOrNull() ?: return false
        if (age !in 10..100) return false

        // Always validate and store in kg
        val weightKg = if (_useLbs.value) weightDisplay / 2.20462f else weightDisplay
        if (weightKg !in 30f..250f) return false

        // Parse optional HR fields — null if blank or invalid
        val restingHr = _restingHr.value.toIntOrNull()
        val maxHr = _maxHr.value.toIntOrNull()

        repository.saveProfile(UserProfile(age, weightKg, _sex.value, restingHr, maxHr, _useLbs.value))
        return true
    }
}
