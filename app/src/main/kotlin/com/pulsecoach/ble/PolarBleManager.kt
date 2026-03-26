package com.pulsecoach.ble

import android.content.Context
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.pulsecoach.model.BleConnectionState
import com.pulsecoach.model.FoundDevice
import com.pulsecoach.model.HrReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps the Polar BLE SDK and exposes device scanning, connection, and HR streaming
 * as Kotlin StateFlow / Flow — so the rest of the app never touches RxJava directly.
 *
 * This class is created once in the ViewModel and torn down in onCleared().
 *
 * BEGINNER NOTE — Why callbackFlow?
 * The Polar SDK uses RxJava (an older async library). Kotlin prefers Flows.
 * callbackFlow is a bridge: it creates a Flow that runs a block, lets you emit values
 * into it from callbacks, and cleans up when the collector stops listening.
 */
class PolarBleManager(context: Context) {

    // The official Polar BLE API instance. We request the HR and device-info features.
    // applicationContext avoids a memory leak — never store a reference to an Activity.
    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        context.applicationContext,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
        )
    )

    // MutableStateFlow is our internal writeable state container.
    // StateFlow is like a live variable: anyone observing it gets the latest value
    // immediately when they start listening, and again whenever it changes.
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)

    /** Public read-only view of the connection state. The ViewModel exposes this to the UI. */
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    init {
        // Register the SDK callback. The SDK calls these functions when connection events happen.
        // BEGINNER TRAP: if you forget to call api.shutDown() in onCleared(), this callback
        // holds a reference to this object, causing a memory leak.
        api.setApiCallback(object : PolarBleApiCallback() {

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                _connectionState.value = BleConnectionState.Connected(
                    deviceId = polarDeviceInfo.deviceId,
                    deviceName = polarDeviceInfo.name ?: polarDeviceInfo.deviceId
                )
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                _connectionState.value = BleConnectionState.Disconnected
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                _connectionState.value = BleConnectionState.Connecting(polarDeviceInfo.deviceId)
            }

            // bleSdkFeatureReady fires when a feature (like HR streaming) becomes available
            // after connection. We don't need to act on it here — the ViewModel will start
            // the HR stream after the Connected state is observed.
            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) { /* no-op for now */ }

            override fun disInformationReceived(identifier: String, uuid: java.util.UUID, value: String) {
                /* Device information — not needed in Phase 1 */
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                /* Could display battery in UI later */
            }
        })
    }

    /**
     * Scans for nearby Polar devices and emits each one as it is found.
     *
     * Returns a Flow — the caller collects it in a coroutine. The scan stops
     * automatically when the collector's scope is cancelled (e.g. when the user
     * leaves the screen).
     *
     * BEGINNER NOTE — callbackFlow pattern:
     * 1. We subscribe to the RxJava Flowable from the SDK.
     * 2. On each device found, we call trySend() to push it into the Flow.
     * 3. awaitClose() runs when the Flow collector cancels — we dispose the RxJava
     *    subscription to stop the BLE scan and free radio resources.
     */
    fun scanForDevices(): Flow<FoundDevice> = callbackFlow {
        _connectionState.value = BleConnectionState.Scanning

        val disposable = api.searchForDevice()
            .subscribe(
                { deviceInfo ->
                    trySend(
                        FoundDevice(
                            id = deviceInfo.deviceId,
                            name = deviceInfo.name ?: deviceInfo.deviceId,
                            rssi = deviceInfo.rssi
                        )
                    )
                },
                { error ->
                    _connectionState.value = BleConnectionState.Error("Scan failed: ${error.message}")
                    close(error) // Signals the Flow that no more items are coming
                }
            )

        // awaitClose is called when the Flow collector stops (scope cancelled or explicit close).
        // This is where cleanup happens — disposing the RxJava subscription stops the BLE scan.
        awaitClose {
            disposable.dispose()
            // Only reset state if we're still in Scanning — don't clobber a Connected state
            if (_connectionState.value is BleConnectionState.Scanning) {
                _connectionState.value = BleConnectionState.Disconnected
            }
        }
    }

    /**
     * Connects to a Polar device by its ID (e.g. "B5D1234").
     * Connection state updates arrive asynchronously via the PolarBleApiCallback above.
     */
    fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
            _connectionState.value = BleConnectionState.Connecting(deviceId)
        } catch (e: PolarInvalidArgument) {
            _connectionState.value = BleConnectionState.Error("Invalid device ID: $deviceId")
        }
    }

    /**
     * Disconnects from the currently connected device.
     */
    fun disconnectFromDevice(deviceId: String) {
        try {
            api.disconnectFromDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            // Already disconnected — not an error worth surfacing
        }
    }

    /**
     * Streams live heart rate data from a connected Polar device.
     *
     * Emits one [HrReading] per second (H10 native rate).
     * The stream ends if the device disconnects or the collector's scope is cancelled.
     *
     * Call this only after observing [BleConnectionState.Connected].
     */
    fun streamHeartRate(deviceId: String): Flow<HrReading> = callbackFlow {
        val disposable = api.startHrStreaming(deviceId)
            .subscribe(
                { hrData: PolarHrData ->
                    // PolarHrData can contain multiple samples per callback,
                    // but the H10 typically sends one. We emit all of them.
                    hrData.samples.forEach { sample ->
                        trySend(
                            HrReading(
                                bpm = sample.hr,
                                timestampMs = System.currentTimeMillis(),
                                contactOk = sample.contactStatus
                            )
                        )
                    }
                },
                { error ->
                    _connectionState.value = BleConnectionState.Error("HR stream error: ${error.message}")
                    close(error)
                },
                {
                    // onComplete — the stream ended cleanly (e.g. device disconnected)
                    close()
                }
            )

        awaitClose { disposable.dispose() }
    }

    /**
     * Releases all Polar SDK resources. Call this from the ViewModel's onCleared().
     * Forgetting to call this is a classic memory/resource leak.
     */
    fun shutdown() {
        api.shutDown()
    }
}
