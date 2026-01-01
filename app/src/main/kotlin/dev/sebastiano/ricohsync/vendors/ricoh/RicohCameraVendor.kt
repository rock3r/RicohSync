package dev.sebastiano.ricohsync.vendors.ricoh

import dev.sebastiano.ricohsync.domain.vendor.CameraCapabilities
import dev.sebastiano.ricohsync.domain.vendor.CameraGattSpec
import dev.sebastiano.ricohsync.domain.vendor.CameraProtocol
import dev.sebastiano.ricohsync.domain.vendor.CameraVendor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ricoh camera vendor implementation.
 *
 * Supports Ricoh cameras including:
 * - GR IIIx
 * - GR III (likely compatible, untested)
 * - Other Ricoh cameras using the same BLE protocol
 */
@OptIn(ExperimentalUuidApi::class)
object RicohCameraVendor : CameraVendor {

    override val vendorId: String = "ricoh"

    override val vendorName: String = "Ricoh"

    override val gattSpec: CameraGattSpec = RicohGattSpec

    override val protocol: CameraProtocol = RicohProtocol

    override fun recognizesDevice(deviceName: String?, serviceUuids: List<Uuid>): Boolean {
        // Ricoh cameras advertise a specific service UUID
        val hasRicohService =
            serviceUuids.any { uuid -> RicohGattSpec.scanFilterServiceUuids.contains(uuid) }

        // Additional check: device name typically starts with "GR" or "RICOH"
        val hasRicohName =
            deviceName?.let { name ->
                RicohGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        // Accept device if it has the Ricoh service UUID or a recognized name
        return hasRicohService || hasRicohName
    }

    override fun getCapabilities(): CameraCapabilities {
        return CameraCapabilities(
            supportsFirmwareVersion = true,
            supportsDeviceName = true,
            supportsDateTimeSync = true,
            supportsGeoTagging = true,
            supportsLocationSync = true,
        )
    }
}
