package com.pulsecoach.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.Session
import com.pulsecoach.repository.SessionRepository
import com.pulsecoach.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
     * Most recent export result — null means idle (no pending result to show).
     * The screen resets this to null after displaying the snackbar.
     */
    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

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
}
