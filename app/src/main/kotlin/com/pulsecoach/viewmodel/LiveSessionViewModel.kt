package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.ble.PolarBleManager
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.BleConnectionState
import com.pulsecoach.model.FoundDevice
import com.pulsecoach.model.HrReading
import com.pulsecoach.model.ZoneConfig
import com.pulsecoach.repository.ZoneConfigRepository
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
    private val zoneRepository = ZoneConfigRepository(
        PulseCoachDatabase.getInstance(application).zoneConfigDao()
    )

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

    companion object {
        private const val MAX_HR_HISTORY = 60 // 60 seconds of data
    }

    // We keep references to these Jobs so we can cancel them explicitly
    // (e.g. user taps Stop Scan, or device disconnects).
    private var scanJob: Job? = null
    private var hrJob: Job? = null

    init {
        // Watch the connection state. When a device connects, start streaming HR.
        // When it disconnects, cancel the HR stream.
        // This runs for the lifetime of the ViewModel.
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
                    // Avoid duplicates — the H10 may advertise multiple times
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

    private fun startHrStream(deviceId: String) {
        hrJob?.cancel()
        hrJob = viewModelScope.launch {
            bleManager.streamHeartRate(deviceId)
                .catch { /* connectionState already reflects the error */ }
                .collect { reading ->
                    _latestHr.value = reading
                    _currentZone.value = ZoneCalculator.zoneForBpm(reading.bpm, zoneConfig.value)
                    // Append to history, dropping oldest if over the cap
                    _hrHistory.value = (_hrHistory.value + reading).takeLast(MAX_HR_HISTORY)
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
