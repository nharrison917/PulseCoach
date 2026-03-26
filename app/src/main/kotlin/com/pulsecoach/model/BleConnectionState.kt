package com.pulsecoach.model

/**
 * Represents every possible state of the Polar BLE connection lifecycle.
 *
 * Using a sealed class means the compiler knows all possible states at compile time,
 * which lets us write exhaustive `when` expressions (like a switch statement that
 * the compiler verifies covers every case).
 */
sealed class BleConnectionState {

    /** No connection attempt has been made, or the device was disconnected. */
    data object Disconnected : BleConnectionState()

    /** Actively scanning for nearby Polar devices. */
    data object Scanning : BleConnectionState()

    /**
     * Scan completed and at least one device was found.
     * @param devices List of found devices, each with an id and name.
     */
    data class DevicesFound(val devices: List<FoundDevice>) : BleConnectionState()

    /** A specific device has been selected and we are connecting to it. */
    data class Connecting(val deviceId: String) : BleConnectionState()

    /** Successfully connected and the HR stream is active. */
    data class Connected(val deviceId: String, val deviceName: String) : BleConnectionState()

    /** Something went wrong — the message explains what. */
    data class Error(val message: String) : BleConnectionState()
}

/**
 * A Polar device discovered during a BLE scan.
 * @param id  The Polar device ID (e.g. "B5D1234") — used to connect and stream.
 * @param name Human-readable device name (e.g. "Polar H10 B5D1234").
 * @param rssi Signal strength in dBm — more negative = further away.
 */
data class FoundDevice(
    val id: String,
    val name: String,
    val rssi: Int
)
