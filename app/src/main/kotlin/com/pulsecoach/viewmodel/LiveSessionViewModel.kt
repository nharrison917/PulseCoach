package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.ble.PolarBleManager
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.BleConnectionState
import com.pulsecoach.model.FoundDevice
import com.pulsecoach.model.HrReading
import com.pulsecoach.model.HrSample
import com.pulsecoach.model.UserProfile
import com.pulsecoach.model.ZoneConfig
import com.pulsecoach.repository.SessionRepository
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.repository.ZoneConfigRepository
import com.pulsecoach.util.CalorieCalculator
import com.pulsecoach.util.PolynomialProjector
import com.pulsecoach.util.ZoneCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the live training session screen.
 *
 * Owns the [PolarBleManager] and exposes its state as [StateFlow]s for the UI.
 * All coroutines run on [viewModelScope], which is automatically cancelled when
 * the user navigates away — preventing leaks and orphaned background work.
 *
 * AndroidViewModel vs ViewModel: we extend AndroidViewModel because PolarBleManager
 * needs a Context. AndroidViewModel provides the Application context, which is safe
 * to hold long-term (unlike an Activity context, which would cause a memory leak).
 */
class LiveSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = PolarBleManager(application)
    private val db = PulseCoachDatabase.getInstance(application)
    private val zoneRepository = ZoneConfigRepository(db.zoneConfigDao())
    private val sessionRepository = SessionRepository(db.sessionDao(), db.hrSampleDao())
    private val userProfileRepository = UserProfileRepository(application)

    // Load the user profile once at startup. SharedPreferences is synchronous so
    // this is safe to call here. Stored as a plain var — profile changes only take
    // effect when the user starts a new session (which is the expected UX).
    private var userProfile: UserProfile? = userProfileRepository.getProfile()

    /** Active zone thresholds — stays in sync with whatever the user last saved. */
    val zoneConfig: StateFlow<ZoneConfig> = zoneRepository.zoneConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneConfig.defaults)

    /** Mirrors the BLE connection state from PolarBleManager. */
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState

    /** Devices found during the current scan, cleared on each new scan. */
    private val _foundDevices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val foundDevices: StateFlow<List<FoundDevice>> = _foundDevices.asStateFlow()

    /** The most recent HR reading from the H10, or null before first reading. */
    private val _latestHr = MutableStateFlow<HrReading?>(null)
    val latestHr: StateFlow<HrReading?> = _latestHr.asStateFlow()

    /** Current HR zone (1–5), or 0 when there is no reading. */
    private val _currentZone = MutableStateFlow(0)
    val currentZone: StateFlow<Int> = _currentZone.asStateFlow()

    /**
     * Rolling buffer of recent readings for the chart. Capped at [MAX_HR_HISTORY] points
     * (60 seconds at one reading/second). Using a list lets Vico index directly into it.
     */
    private val _hrHistory = MutableStateFlow<List<HrReading>>(emptyList())
    val hrHistory: StateFlow<List<HrReading>> = _hrHistory.asStateFlow()

    /** Calories per minute at the current heart rate. 0f when HR is below 90 bpm. */
    private val _currentCalPerMinute = MutableStateFlow(0f)
    val currentCalPerMinute: StateFlow<Float> = _currentCalPerMinute.asStateFlow()

    /** True while a session is actively being recorded to Room. */
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Running total of calories burned since recording started. */
    private val _sessionTotalCalories = MutableStateFlow(0f)
    val sessionTotalCalories: StateFlow<Float> = _sessionTotalCalories.asStateFlow()

    /** Running average bpm for the current recording. 0f when not recording. */
    private val _sessionAvgBpm = MutableStateFlow(0f)
    val sessionAvgBpm: StateFlow<Float> = _sessionAvgBpm.asStateFlow()

    /** Running average cal/min for the current recording. 0f when not recording. */
    private val _sessionAvgCalPerMinute = MutableStateFlow(0f)
    val sessionAvgCalPerMinute: StateFlow<Float> = _sessionAvgCalPerMinute.asStateFlow()

    /**
     * Elapsed recording time in whole seconds. Derived from [sampleCount] because
     * the H10 sends one sample per second — no separate timer coroutine needed.
     * Resets to 0 on each new session.
     */
    private val _sessionElapsedSeconds = MutableStateFlow(0)
    val sessionElapsedSeconds: StateFlow<Int> = _sessionElapsedSeconds.asStateFlow()

    /**
     * Target session duration selected by the user before recording starts.
     * Drives the projection endpoint. Default 45 minutes.
     */
    private val _targetDurationMinutes = MutableStateFlow(45)
    val targetDurationMinutes: StateFlow<Int> = _targetDurationMinutes.asStateFlow()

    /**
     * Per-minute cumulative calorie data for the calorie chart.
     * One point added per minute while recording. Resets on each new session.
     * x = elapsed minutes, y = cumulative calories at that minute.
     */
    private val _actualCalorieCurve = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val actualCalorieCurve: StateFlow<List<Pair<Float, Float>>> = _actualCalorieCurve.asStateFlow()

    /**
     * Polynomial projection of the calorie curve to [targetDurationMinutes].
     * Null until [PolynomialProjector.MIN_DATA_MINUTES] of data have been recorded.
     */
    private val _projectedCalorieCurve = MutableStateFlow<List<Pair<Float, Float>>?>(null)
    val projectedCalorieCurve: StateFlow<List<Pair<Float, Float>>?> = _projectedCalorieCurve.asStateFlow()

    // --- Session recording accumulators ---
    // These are only written from coroutines on Dispatchers.Main (viewModelScope default),
    // so no synchronization is needed — main-thread coroutines are sequential.
    private var activeSessionId: Long? = null
    private var cumulativeCalories: Float = 0f
    private var totalBpmAccumulator: Long = 0L  // sum of all bpm readings this session
    private var sampleCount: Int = 0            // number of readings this session
    // Tracks the last whole minute for which a calorie curve point was emitted.
    // -1 means no point has been added yet for the current recording.
    private var calorieCurveLastMinute: Int = -1

    companion object {
        private const val MAX_HR_HISTORY = 60
    }

    private var scanJob: Job? = null
    private var hrJob: Job? = null

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    is BleConnectionState.Connected -> startHrStream(state.deviceId)
                    else -> {
                        hrJob?.cancel()
                        hrJob = null
                        _latestHr.value = null
                        _currentZone.value = 0
                        _hrHistory.value = emptyList()
                        _currentCalPerMinute.value = 0f
                        // Note: do NOT auto-stop recording here — a signal drop mid-session
                        // should not discard the session. The user taps Stop explicitly.
                    }
                }
            }
        }
    }

    /** Starts a BLE scan, accumulating found devices into [foundDevices]. */
    fun startScan() {
        scanJob?.cancel()
        _foundDevices.value = emptyList()

        scanJob = viewModelScope.launch {
            bleManager.scanForDevices()
                .catch { /* connectionState already reflects the error */ }
                .collect { device ->
                    if (_foundDevices.value.none { it.id == device.id }) {
                        _foundDevices.value = _foundDevices.value + device
                    }
                }
        }
    }

    /** Stops an in-progress scan. */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    /** Stops the scan (if running) and initiates a connection to the selected device. */
    fun connectToDevice(deviceId: String) {
        stopScan()
        bleManager.connectToDevice(deviceId)
    }

    /** Disconnects from the current device. */
    fun disconnect(deviceId: String) {
        hrJob?.cancel()
        hrJob = null
        bleManager.disconnectFromDevice(deviceId)
    }

    /**
     * Updates the target session duration. Only effective before recording starts;
     * changing it mid-session does not retroactively alter the projection endpoint.
     */
    fun setTargetDuration(minutes: Int) {
        _targetDurationMinutes.value = minutes
    }

    /**
     * Starts writing HR samples to Room. Creates a new session row immediately
     * so the session ID is available for every subsequent sample insert.
     * No-op if already recording or if no user profile is set.
     */
    fun startRecording() {
        if (_isRecording.value) return
        if (userProfile == null) return

        viewModelScope.launch {
            val sessionId = sessionRepository.startSession(System.currentTimeMillis())
            activeSessionId = sessionId
            cumulativeCalories = 0f
            totalBpmAccumulator = 0L
            sampleCount = 0
            calorieCurveLastMinute = -1
            _sessionTotalCalories.value = 0f
            _sessionAvgBpm.value = 0f
            _sessionAvgCalPerMinute.value = 0f
            _sessionElapsedSeconds.value = 0
            _actualCalorieCurve.value = emptyList()
            _projectedCalorieCurve.value = null
            _isRecording.value = true
        }
    }

    /**
     * Stops recording and writes the session summary (total calories, avg bpm, end time).
     * No-op if not currently recording.
     */
    fun stopRecording() {
        val sessionId = activeSessionId ?: return

        val avgBpm = if (sampleCount > 0) totalBpmAccumulator.toFloat() / sampleCount else 0f

        viewModelScope.launch {
            sessionRepository.finishSession(
                sessionId = sessionId,
                endTimeMs = System.currentTimeMillis(),
                totalCalories = cumulativeCalories,
                avgBpm = avgBpm
            )
            activeSessionId = null
            _isRecording.value = false
            _currentCalPerMinute.value = 0f
            _sessionTotalCalories.value = 0f
            _sessionAvgBpm.value = 0f
            _sessionAvgCalPerMinute.value = 0f
            _sessionElapsedSeconds.value = 0
            _actualCalorieCurve.value = emptyList()
            _projectedCalorieCurve.value = null
        }
    }

    private fun startHrStream(deviceId: String) {
        hrJob?.cancel()
        hrJob = viewModelScope.launch {
            bleManager.streamHeartRate(deviceId)
                .catch { /* connectionState already reflects the error */ }
                .collect { reading ->
                    val zone = ZoneCalculator.zoneForBpm(reading.bpm, zoneConfig.value)
                    _latestHr.value = reading
                    _currentZone.value = zone
                    _hrHistory.value = (_hrHistory.value + reading).takeLast(MAX_HR_HISTORY)

                    // Compute calorie rate regardless of recording state so the
                    // live cal/min display works even before the user taps Start.
                    val profile = userProfile
                    val calPerMin = if (profile != null) {
                        CalorieCalculator.calPerMinute(reading.bpm, profile)
                    } else 0f
                    _currentCalPerMinute.value = calPerMin

                    // Write to Room only while a session is active
                    val sessionId = activeSessionId
                    if (_isRecording.value && sessionId != null && profile != null) {
                        // Each reading covers 1 second = 1/60 of a minute
                        cumulativeCalories += CalorieCalculator.calPerSample(reading.bpm, profile)
                        totalBpmAccumulator += reading.bpm
                        sampleCount++
                        _sessionTotalCalories.value = cumulativeCalories
                        // Averages: avg bpm = sum/count; avg cal/min = totalCal * 60 / count
                        // (the *60 converts from cumulative-seconds to per-minute rate)
                        _sessionAvgBpm.value = totalBpmAccumulator.toFloat() / sampleCount
                        _sessionAvgCalPerMinute.value = cumulativeCalories * 60f / sampleCount
                        _sessionElapsedSeconds.value = sampleCount

                        // Calorie curve: add one point per elapsed minute.
                        // sampleCount / 60 gives the completed-minute count (integer division).
                        val elapsedMinute = sampleCount / 60
                        if (elapsedMinute > calorieCurveLastMinute) {
                            calorieCurveLastMinute = elapsedMinute
                            val updatedCurve = _actualCalorieCurve.value +
                                (elapsedMinute.toFloat() to cumulativeCalories)
                            _actualCalorieCurve.value = updatedCurve

                            // Recompute projection once per minute after MIN_DATA_MINUTES.
                            // Project against a snapshot of targetDurationMinutes so the
                            // endpoint stays stable even if the user somehow changes it.
                            if (elapsedMinute >= PolynomialProjector.MIN_DATA_MINUTES.toInt()) {
                                _projectedCalorieCurve.value = PolynomialProjector.project(
                                    dataPoints = updatedCurve,
                                    targetMinutes = _targetDurationMinutes.value.toFloat()
                                )
                            }
                        }

                        sessionRepository.insertSample(
                            HrSample(
                                id = 0,          // 0 = let Room auto-generate the ID
                                sessionId = sessionId,
                                timestampMs = reading.timestampMs,
                                bpm = reading.bpm,
                                zone = zone,
                                calPerMinute = calPerMin,
                                cumulativeCalories = cumulativeCalories
                            )
                        )
                    }
                }
        }
    }

    // BEGINNER TRAP: forgetting onCleared() means PolarBleManager is never shut down,
    // leaving the BLE radio resource open after the screen is gone.
    override fun onCleared() {
        super.onCleared()
        bleManager.shutdown()
    }
}
