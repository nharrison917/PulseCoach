package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.UserProfile
import com.pulsecoach.repository.UserProfileRepository
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

    private val _weightKg = MutableStateFlow("")
    val weightKg: StateFlow<String> = _weightKg.asStateFlow()

    private val _sex = MutableStateFlow(BiologicalSex.MALE)
    val sex: StateFlow<BiologicalSex> = _sex.asStateFlow()

    init {
        // Pre-populate fields if the user is editing an existing profile
        val existing = repository.getProfile()
        if (existing != null) {
            _age.value = existing.age.toString()
            _weightKg.value = existing.weightKg.toString()
            _sex.value = existing.sex
        }
    }

    /** Updates the draft age string as the user types. */
    fun onAgeChange(value: String) { _age.value = value }

    /** Updates the draft weight string as the user types. */
    fun onWeightChange(value: String) { _weightKg.value = value }

    /** Updates the selected biological sex. */
    fun onSexChange(value: BiologicalSex) { _sex.value = value }

    /**
     * Validates fields and saves the profile.
     * @return true if save succeeded, false if any field is out of range.
     */
    fun saveProfile(): Boolean {
        val age = _age.value.toIntOrNull() ?: return false
        val weight = _weightKg.value.toFloatOrNull() ?: return false
        if (age !in 10..100) return false
        if (weight !in 30f..250f) return false
        repository.saveProfile(UserProfile(age, weight, _sex.value))
        return true
    }
}
