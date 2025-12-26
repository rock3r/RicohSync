package dev.sebastiano.ricohsync.vendors.sony

import dev.sebastiano.ricohsync.domain.vendor.CameraCapabilities
import dev.sebastiano.ricohsync.domain.vendor.CameraGattSpec
import dev.sebastiano.ricohsync.domain.vendor.CameraProtocol
import dev.sebastiano.ricohsync.domain.vendor.CameraVendor
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sony camera vendor implementation.
 *
 * Supports Sony Alpha cameras using the DI Remote Control protocol.
 */
@OptIn(ExperimentalUuidApi::class)
object SonyCameraVendor : CameraVendor {

    override val vendorId: String = "sony"

    override val vendorName: String = "Sony"

    override val gattSpec: CameraGattSpec = SonyGattSpec

    override val protocol: CameraProtocol = SonyProtocol

    override fun recognizesDevice(deviceName: String?, serviceUuids: List<Uuid>): Boolean {
        // Sony cameras using DI Remote Control advertise a specific service UUID
        return serviceUuids.any { uuid ->
            SonyGattSpec.scanFilterServiceUuids.contains(uuid)
        }
    }

    override fun getCapabilities(): CameraCapabilities {
        return CameraCapabilities(
            supportsFirmwareVersion = true, // Standard DIS
            supportsDeviceName = false, // Setting device name is not standard for Sony via BLE
            supportsDateTimeSync = true,
            supportsGeoTagging = false, // No separate toggle; location data includes time
            supportsLocationSync = true,
        )
    }
}
