package com.pulsecoach.repository

import android.content.Context
import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.UserProfile

/**
 * Reads and writes user profile values to SharedPreferences.
 *
 * SharedPreferences is used instead of Room because this is a single-record,
 * key-value store — not tabular data. It also lets MainActivity read the
 * profile_complete flag synchronously before the first composable renders.
 */
class UserProfileRepository(context: Context) {

    // MODE_PRIVATE: only this app can read this file
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AGE = "profile_age"
        private const val KEY_WEIGHT_KG = "profile_weight_kg"
        private const val KEY_SEX = "profile_sex"
        private const val KEY_RESTING_HR = "profile_resting_hr"
        private const val KEY_MAX_HR = "profile_max_hr"
        private const val KEY_USE_LBS = "profile_use_lbs"
        const val KEY_COMPLETE = "profile_complete"
    }

    /** Returns true if the user has completed first-launch setup. */
    fun isProfileComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    /**
     * Returns the saved profile, or null if setup has not been completed.
     */
    fun getProfile(): UserProfile? {
        if (!isProfileComplete()) return null
        // -1 is the "not set" sentinel — Int? can't be stored directly in SharedPreferences
        val restingHr = prefs.getInt(KEY_RESTING_HR, -1).takeIf { it != -1 }
        val maxHr = prefs.getInt(KEY_MAX_HR, -1).takeIf { it != -1 }
        return UserProfile(
            age = prefs.getInt(KEY_AGE, 0),
            weightKg = prefs.getFloat(KEY_WEIGHT_KG, 0f),
            sex = BiologicalSex.valueOf(
                prefs.getString(KEY_SEX, BiologicalSex.MALE.name)!!
            ),
            restingHr = restingHr,
            maxHr = maxHr,
            useLbs = prefs.getBoolean(KEY_USE_LBS, false)
        )
    }

    /**
     * Saves the profile and marks setup as complete.
     * apply() writes asynchronously to disk — safe here since we don't need
     * the value to persist before the next line runs.
     */
    fun saveProfile(profile: UserProfile) {
        val editor = prefs.edit()
            .putInt(KEY_AGE, profile.age)
            .putFloat(KEY_WEIGHT_KG, profile.weightKg)
            .putString(KEY_SEX, profile.sex.name)
            .putBoolean(KEY_USE_LBS, profile.useLbs)
            .putBoolean(KEY_COMPLETE, true)
        // Store -1 when null so we can distinguish "not set" from 0
        if (profile.restingHr != null) editor.putInt(KEY_RESTING_HR, profile.restingHr)
        else editor.remove(KEY_RESTING_HR)
        if (profile.maxHr != null) editor.putInt(KEY_MAX_HR, profile.maxHr)
        else editor.remove(KEY_MAX_HR)
        editor.apply()
    }
}
