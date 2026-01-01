package dev.sebastiano.ricohsync.domain.model

import dev.sebastiano.ricohsync.domain.vendor.CameraVendor

/**
 * Represents a paired camera device stored in the app.
 *
 * This is the domain model for a device that has been previously paired.
 * It contains both the persistent device information and runtime state.
 *
 * @param macAddress The Bluetooth MAC address (unique identifier).
 * @param name Human-readable device name (e.g., "GR IIIx").
 * @param vendorId Identifier for the camera vendor (e.g., "ricoh").
 * @param isEnabled Whether the device should be auto-connected and synced.
 */
data class PairedDevice(
    val macAddress: String,
    val name: String?,
    val vendorId: String,
    val isEnabled: Boolean,
)

/**
 * Represents the current connection state of a paired device.
 */
sealed interface DeviceConnectionState {
    /** Device is not enabled for sync. */
    data object Disabled : DeviceConnectionState

    /** Device is enabled but not yet connected. */
    data object Disconnected : DeviceConnectionState

    /** Actively trying to connect to the device. */
    data object Connecting : DeviceConnectionState

    /** Connected and ready for sync. */
    data class Connected(
        val firmwareVersion: String? = null,
    ) : DeviceConnectionState

    /** Connection failed with an error. */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true,
    ) : DeviceConnectionState

    /** Actively syncing data to the device. */
    data class Syncing(
        val firmwareVersion: String? = null,
        val lastSyncInfo: LocationSyncInfo? = null,
    ) : DeviceConnectionState
}

/**
 * A paired device combined with its current connection state.
 * This is what the UI layer observes.
 */
data class PairedDeviceWithState(
    val device: PairedDevice,
    val connectionState: DeviceConnectionState,
) {
    val isConnected: Boolean
        get() = connectionState is DeviceConnectionState.Connected ||
            connectionState is DeviceConnectionState.Syncing
}

/**
 * Converts a [PairedDevice] to a [Camera] for use with the camera repository.
 *
 * @param vendor The resolved [CameraVendor] for this device.
 */
fun PairedDevice.toCamera(vendor: CameraVendor): Camera = Camera(
    identifier = macAddress,
    name = name,
    macAddress = macAddress,
    vendor = vendor,
)

/**
 * Creates a [PairedDevice] from a discovered [Camera].
 *
 * @param enabled Whether the device should be initially enabled.
 */
fun Camera.toPairedDevice(enabled: Boolean = true): PairedDevice = PairedDevice(
    macAddress = macAddress,
    name = name,
    vendorId = vendor.vendorId,
    isEnabled = enabled,
)

