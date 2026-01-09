package dev.sebastiano.camerasync.vendors.sony

import dev.sebastiano.camerasync.domain.vendor.CameraCapabilities
import dev.sebastiano.camerasync.domain.vendor.CameraGattSpec
import dev.sebastiano.camerasync.domain.vendor.CameraProtocol
import dev.sebastiano.camerasync.domain.vendor.CameraVendor
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
        val hasSonyService =
            serviceUuids.any { uuid -> SonyGattSpec.scanFilterServiceUuids.contains(uuid) }

        // If service UUIDs are provided, only recognize if Sony service UUID is present
        // (Don't trust name matching when service UUIDs are explicitly provided)
        if (serviceUuids.isNotEmpty()) {
            return hasSonyService
        }

        // If no service UUIDs are provided, fall back to name pattern matching
        // This handles cases where service UUIDs aren't advertised in the scan
        val hasSonyName =
            deviceName?.let { name ->
                SonyGattSpec.scanFilterDeviceNames.any { prefix ->
                    name.startsWith(prefix, ignoreCase = true)
                }
            } ?: false

        return hasSonyName
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

    override fun extractModelFromPairingName(pairingName: String?): String {
        if (pairingName == null) return "Unknown"

        val name = pairingName.trim()

        // Sony cameras typically use ILCE- prefix for Alpha cameras
        // Try to extract model from common patterns
        val ilcePattern = Regex("ILCE-?([0-9A-Z]+)", RegexOption.IGNORE_CASE)
        ilcePattern.find(name)?.let { match ->
            return "ILCE-${match.groupValues[1]}"
        }

        // If name starts with known Sony prefixes, assume it's a model
        if (
            name.startsWith("ILCE", ignoreCase = true) || name.startsWith("DSC-", ignoreCase = true)
        ) {
            return name
        }

        // Fallback: return the pairing name as-is
        return name
    }
}
