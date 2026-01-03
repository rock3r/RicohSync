package dev.sebastiano.ricohsync.domain.vendor

import dev.sebastiano.ricohsync.domain.model.GpsLocation
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a camera vendor (manufacturer/model family).
 *
 * This interface defines the vendor-specific BLE protocol details that enable RicohSync to
 * communicate with cameras from different manufacturers.
 *
 * Each vendor implementation encapsulates:
 * - BLE GATT service and characteristic UUIDs
 * - Binary protocol encoding/decoding
 * - Device identification and scanning
 * - Vendor-specific capabilities
 */
interface CameraVendor {

    /** Unique identifier for this vendor (e.g., "ricoh", "canon", "nikon"). */
    val vendorId: String

    /** Human-readable vendor name (e.g., "Ricoh", "Canon", "Nikon"). */
    val vendorName: String

    /** GATT specification for this vendor's cameras. */
    val gattSpec: CameraGattSpec

    /** Protocol encoder/decoder for this vendor. */
    val protocol: CameraProtocol

    /**
     * Determines if a discovered BLE device belongs to this vendor.
     *
     * @param deviceName The advertised device name, or null if not available.
     * @param serviceUuids The list of advertised service UUIDs.
     * @return true if this vendor recognizes the device.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun recognizesDevice(deviceName: String?, serviceUuids: List<Uuid>): Boolean

    /**
     * Returns the device capabilities for this vendor.
     *
     * Different vendors may support different features (e.g., geo-tagging, time sync, firmware
     * version reading, etc.).
     */
    fun getCapabilities(): CameraCapabilities

    /**
     * Extracts the camera model from a pairing name.
     *
     * This method attempts to identify the actual camera model from the pairing name, which may
     * have been customized by the user. For example, if a user renamed their "GR IIIx" to "My
     * Camera", this method should still return "GR IIIx" as the model.
     *
     * @param pairingName The pairing name (may be user-customized).
     * @return The extracted model name, or the pairing name itself if the model cannot be
     *   determined.
     */
    fun extractModelFromPairingName(pairingName: String?): String
}

/** Defines the BLE GATT service and characteristic UUIDs for a camera vendor. */
@OptIn(ExperimentalUuidApi::class)
interface CameraGattSpec {

    /** Service UUID(s) used for scanning and filtering camera advertisements. */
    val scanFilterServiceUuids: List<Uuid>

    /** Device name prefix(es) used for scanning and filtering camera advertisements. */
    val scanFilterDeviceNames: List<String>
        get() = emptyList()

    /** Firmware version service UUID, or null if not supported. */
    val firmwareServiceUuid: Uuid?

    /** Firmware version characteristic UUID, or null if not supported. */
    val firmwareVersionCharacteristicUuid: Uuid?

    /** Device name service UUID, or null if not supported. */
    val deviceNameServiceUuid: Uuid?

    /** Device name characteristic UUID, or null if not supported. */
    val deviceNameCharacteristicUuid: Uuid?

    /** Date/time service UUID, or null if not supported. */
    val dateTimeServiceUuid: Uuid?

    /** Date/time characteristic UUID, or null if not supported. */
    val dateTimeCharacteristicUuid: Uuid?

    /** Geo-tagging enable/disable characteristic UUID, or null if not supported. */
    val geoTaggingCharacteristicUuid: Uuid?

    /** Location sync service UUID, or null if not supported. */
    val locationServiceUuid: Uuid?

    /** Location sync characteristic UUID, or null if not supported. */
    val locationCharacteristicUuid: Uuid?
}

/** Handles encoding and decoding of data for a camera vendor's BLE protocol. */
interface CameraProtocol {

    /**
     * Encodes a date/time value to the vendor's binary format.
     *
     * @return Encoded byte array ready to be written to the camera.
     */
    fun encodeDateTime(dateTime: ZonedDateTime): ByteArray

    /**
     * Decodes a date/time value from the vendor's binary format.
     *
     * @return Human-readable string representation of the decoded date/time.
     */
    fun decodeDateTime(bytes: ByteArray): String

    /**
     * Encodes a GPS location to the vendor's binary format.
     *
     * @return Encoded byte array ready to be written to the camera.
     */
    fun encodeLocation(location: GpsLocation): ByteArray

    /**
     * Decodes a GPS location from the vendor's binary format.
     *
     * @return Human-readable string representation of the decoded location.
     */
    fun decodeLocation(bytes: ByteArray): String

    /**
     * Encodes the geo-tagging enabled/disabled state.
     *
     * @return Encoded byte array.
     */
    fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray

    /**
     * Decodes the geo-tagging enabled/disabled state.
     *
     * @return true if geo-tagging is enabled.
     */
    fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean
}

/** Defines the capabilities supported by a camera vendor. */
data class CameraCapabilities(
    /** Whether the camera supports reading firmware version. */
    val supportsFirmwareVersion: Boolean = false,

    /** Whether the camera supports setting a paired device name. */
    val supportsDeviceName: Boolean = false,

    /** Whether the camera supports date/time synchronization. */
    val supportsDateTimeSync: Boolean = false,

    /** Whether the camera supports enabling/disabling geo-tagging. */
    val supportsGeoTagging: Boolean = false,

    /** Whether the camera supports GPS location synchronization. */
    val supportsLocationSync: Boolean = false,
)
