package com.pulsecoach.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.Session
import com.pulsecoach.model.SessionType
import com.pulsecoach.repository.SessionRepository
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.util.CsvExporter
import com.pulsecoach.util.ProjectionCalibrator
import com.pulsecoach.util.SyntheticSessionGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of a synthetic session seed operation. */
sealed class SeedingState {
    object Idle : SeedingState()
    object InProgress : SeedingState()
    data class Done(val count: Int) : SeedingState()
    data class Error(val message: String) : SeedingState()
}

/** Tracks the state of a realistic seed operation (sessions + calibration). */
sealed class RealisticSeedingState {
    object Idle : RealisticSeedingState()
    object InProgress : RealisticSeedingState()
    data class Done(val sessionCount: Int, val ratioCount: Int) : RealisticSeedingState()
    data class Error(val message: String) : RealisticSeedingState()
}

/** Result of a CSV export attempt, shown to the user as a snackbar message. */
sealed class ExportResult {
    /** Export succeeded. [fileName] is the name of the file in Downloads. */
    data class Success(val fileName: String) : ExportResult()
    /** Export failed. [message] explains why. */
    data class Error(val message: String) : ExportResult()
}

/**
 * ViewModel for the session history screen.
 *
 * Loads all sessions from Room and handles CSV export via the MediaStore API.
 * Room emits a new list automatically whenever a session is added or updated,
 * so the history list stays current without manual refreshes.
 */
class SessionHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PulseCoachDatabase.getInstance(application)
    private val repository = SessionRepository(db.sessionDao(), db.hrSampleDao())

    /** All sessions ordered newest-first. Empty list while loading. */
    val sessions: StateFlow<List<Session>> = repository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Set of session IDs currently selected for deletion.
     * Non-empty means the screen is in selection mode.
     */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /**
     * Most recent export result — null means idle (no pending result to show).
     * The screen resets this to null after displaying the snackbar.
     */
    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    /** Tracks the state of a synthetic session seeding operation. */
    private val _seedingState = MutableStateFlow<SeedingState>(SeedingState.Idle)
    val seedingState: StateFlow<SeedingState> = _seedingState.asStateFlow()

    /** Tracks the state of a realistic seed operation (sessions + calibration). */
    private val _realisticSeedingState =
        MutableStateFlow<RealisticSeedingState>(RealisticSeedingState.Idle)
    val realisticSeedingState: StateFlow<RealisticSeedingState> =
        _realisticSeedingState.asStateFlow()

    /**
     * Exports all HR samples for [sessionId] to a CSV file in the Downloads folder.
     *
     * Uses the MediaStore API (Android 10+). On older devices, sets an error result.
     * IS_PENDING is set to 1 while writing and cleared to 0 when done — this prevents
     * other apps from seeing a half-written file in the Downloads folder.
     */
    fun exportToCsv(sessionId: Long) {
        viewModelScope.launch {
            // Guard: MediaStore.Downloads requires Android 10 (API 29)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                _exportResult.value = ExportResult.Error("CSV export requires Android 10 or later")
                return@launch
            }

            val session = db.sessionDao().getSessionById(sessionId)
            if (session == null) {
                _exportResult.value = ExportResult.Error("Session not found")
                return@launch
            }

            val samples = repository.getSamplesForSessionOnce(sessionId)
            if (samples.isEmpty()) {
                _exportResult.value = ExportResult.Error("No recorded data for this session")
                return@launch
            }

            val fileName = CsvExporter.fileName(session.startTimeMs)
            val csvContent = CsvExporter.buildCsv(samples)
            val contentResolver = getApplication<Application>().contentResolver

            // Step 1: insert a pending entry so other apps can't see an incomplete file
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                _exportResult.value = ExportResult.Error("Could not create file in Downloads")
                return@launch
            }

            // Step 2: write CSV bytes
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error("Write failed: ${e.message}")
                return@launch
            }

            // Step 3: clear IS_PENDING to publish the file to the Downloads folder
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)

            _exportResult.value = ExportResult.Success(fileName)
        }
    }

    /** Called by the screen after it has displayed the export result snackbar. */
    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * Seeds 12 synthetic sessions into Room using the saved user profile.
     * Only called from debug builds via the Seed button on the history screen.
     *
     * Reads UserProfile from SharedPreferences at seed time (same approach the live
     * screen uses for calorie display).
     */
    fun seedSyntheticSessions() {
        viewModelScope.launch {
            _seedingState.value = SeedingState.InProgress
            val profile = UserProfileRepository(getApplication()).getProfile()
            if (profile == null) {
                _seedingState.value = SeedingState.Error("No user profile saved. Complete setup first.")
                return@launch
            }
            repository.seedSyntheticSessions(profile)
            _seedingState.value = SeedingState.Done(SyntheticSessionGenerator.SESSION_CONFIGS.size)
        }
    }

    /** Called by the screen after it has displayed the seeding result snackbar. */
    fun clearSeedingState() {
        _seedingState.value = SeedingState.Idle
    }

    /** Seeds ~15 realistic synthetic sessions based on user's real workout params. */
    fun seedRealisticSessions(targetHr: Int, noiseSigma: Double) {
        viewModelScope.launch {
            _realisticSeedingState.value = RealisticSeedingState.InProgress
            val profile = UserProfileRepository(getApplication()).getProfile()
            if (profile == null) {
                _realisticSeedingState.value =
                    RealisticSeedingState.Error("No user profile saved.")
                return@launch
            }
            repository.seedRealisticSessions(targetHr, noiseSigma, profile)
            _realisticSeedingState.value = RealisticSeedingState.Done(15, 0)
        }
    }

    /** Seeds [n] calibration ratios centered on [baseRatio] with the given [spread]. */
    fun seedCalibrationRatios(baseRatio: Float, spread: Float = 0.07f, n: Int = 8) {
        ProjectionCalibrator.seedCalibrationRatios(getApplication(), baseRatio, spread, n)
        _realisticSeedingState.value = RealisticSeedingState.Done(0, n)
    }

    /** Called by the screen after it has displayed the realistic seeding result snackbar. */
    fun clearRealisticSeedingState() {
        _realisticSeedingState.value = RealisticSeedingState.Idle
    }

    /** Adds or removes a session from the selection set. */
    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    /** Exits selection mode without deleting anything. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Updates the intensity classification for a single session.
     * Passing null clears the label — Room Flow on getAllSessions() refreshes the list automatically.
     */
    fun updateSessionType(sessionId: Long, type: SessionType?) {
        viewModelScope.launch {
            repository.updateSessionType(sessionId, type)
        }
    }

    /**
     * Permanently deletes all selected sessions and their HR sample data.
     * Room's Flow on getAllSessions() will emit the updated list automatically.
     */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSessions(ids)
            _selectedIds.value = emptySet()
        }
    }
}
