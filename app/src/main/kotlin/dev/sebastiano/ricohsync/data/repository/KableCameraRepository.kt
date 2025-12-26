package dev.sebastiano.ricohsync.data.repository

import android.bluetooth.le.ScanSettings
import android.util.Log
import com.juul.kable.Advertisement
import com.juul.kable.ExperimentalApi
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "KableCameraRepository"

/**
 * Implementation of [CameraRepository] using the Kable BLE library.
 *
 * This repository is vendor-agnostic and supports cameras from multiple manufacturers
 * through the [CameraVendorRegistry].
 */
@OptIn(ExperimentalUuidApi::class)
class KableCameraRepository(
    private val vendorRegistry: CameraVendorRegistry,
) : CameraRepository {

    @OptIn(ObsoleteKableApi::class)
    private val scanner by lazy {
        val scanFilterUuids = vendorRegistry.getAllScanFilterUuids()
        Log.i(TAG, "Scanning for cameras from ${vendorRegistry.getAllVendors().size} vendors")
        Log.i(TAG, "Scan filter UUIDs: $scanFilterUuids")

        com.juul.kable.Scanner {
            filters {
                // Add a filter for each vendor's scan service UUIDs
                scanFilterUuids.forEach { uuid ->
                    match { services = listOf(uuid) }
                }
            }
            logging {
                engine = SystemLogEngine
                level = Logging.Level.Events
                format = Logging.Format.Multiline
            }
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        }
    }

    override val discoveredCameras: Flow<Camera>
        get() = scanner.advertisements.mapNotNull { it.toCamera() }

    override fun startScanning() {
        // Scanner is lazy and starts when advertisements flow is collected
        Log.i(TAG, "Starting camera scan")
    }

    override fun stopScanning() {
        Log.i(TAG, "Stopping camera scan")
        // Scanner stops when the flow collection is cancelled
    }

    override fun findCameraByMacAddress(macAddress: String): Flow<Camera> {
        @OptIn(ObsoleteKableApi::class)
        val scanner = com.juul.kable.Scanner {
            filters {
                match { address = macAddress }
            }
            logging {
                engine = SystemLogEngine
                level = Logging.Level.Events
            }
        }
        return scanner.advertisements.mapNotNull { it.toCamera() }
    }

    override suspend fun connect(camera: Camera): CameraConnection {
        // We need to scan for the device first to get the Advertisement
        // This is a limitation of Kable - we need the Advertisement to create a Peripheral
        val scanner = com.juul.kable.Scanner {
            @OptIn(ObsoleteKableApi::class)
            filters {
                match { address = camera.macAddress }
            }
        }

        val advertisement = scanner.advertisements.first()

        val peripheral = Peripheral(advertisement) {
            logging {
                level = Logging.Level.Events
                engine = SystemLogEngine
                identifier = "CameraSync:${camera.vendor.vendorName}"
            }
        }

        Log.i(TAG, "Connecting to ${camera.name}...")
        peripheral.connect()
        Log.i(TAG, "Connected to ${camera.name}")

        return KableCameraConnection(camera, peripheral)
    }

    /**
     * Converts a BLE Advertisement to a Camera by identifying the vendor.
     *
     * @return A Camera instance if a vendor is recognized, or null if no vendor matches.
     */
    private fun Advertisement.toCamera(): Camera? {
        val vendor = vendorRegistry.identifyVendor(
            deviceName = peripheralName,
            serviceUuids = uuids,
        )

        if (vendor == null) {
            Log.w(TAG, "No vendor recognized for device: $peripheralName (services: $uuids)")
            return null
        }

        Log.i(TAG, "Discovered ${vendor.vendorName} camera: $peripheralName")
        return Camera(
            identifier = identifier,
            name = peripheralName,
            macAddress = identifier, // On Android, identifier is the MAC address
            vendor = vendor,
        )
    }
}

/**
 * Implementation of [CameraConnection] using a Kable Peripheral.
 *
 * This implementation is vendor-agnostic and uses the camera's vendor specification
 * to interact with the camera's BLE services.
 */
@OptIn(ExperimentalUuidApi::class)
internal class KableCameraConnection(
    override val camera: Camera,
    private val peripheral: Peripheral,
) : CameraConnection {

    private val gattSpec = camera.vendor.gattSpec
    private val protocol = camera.vendor.protocol
    private val capabilities = camera.vendor.getCapabilities()

    override suspend fun readFirmwareVersion(): String {
        if (!capabilities.supportsFirmwareVersion) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support firmware version reading"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.firmwareServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.firmwareVersionCharacteristicUuid
        }

        val firmwareBytes = peripheral.read(char)
        val version = firmwareBytes.decodeToString().trimEnd(Char(0))
        Log.i(TAG, "Firmware version: $version")
        return version
    }

    override suspend fun setPairedDeviceName(name: String) {
        if (!capabilities.supportsDeviceName) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support setting paired device name"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.deviceNameServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.deviceNameCharacteristicUuid
        }

        Log.i(TAG, "Setting paired device name: $name")
        peripheral.write(
            characteristic = char,
            data = name.encodeToByteArray(),
            writeType = WriteType.WithResponse,
        )
    }

    override suspend fun syncDateTime(dateTime: ZonedDateTime) {
        if (!capabilities.supportsDateTimeSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support date/time synchronization"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.dateTimeServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.dateTimeCharacteristicUuid
        }

        val data = protocol.encodeDateTime(dateTime)
        Log.i(
            TAG,
            "Syncing date/time: ${protocol.decodeDateTime(data)}",
        )
        peripheral.write(char, data, WriteType.WithResponse)
    }

    override suspend fun readDateTime(): ByteArray {
        if (!capabilities.supportsDateTimeSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support date/time reading"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.dateTimeServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.dateTimeCharacteristicUuid
        }

        val data = peripheral.read(char)
        Log.i(
            TAG,
            "Read camera date/time: ${protocol.decodeDateTime(data)}",
        )
        return data
    }

    override suspend fun setGeoTaggingEnabled(enabled: Boolean) {
        if (!capabilities.supportsGeoTagging) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support geo-tagging control"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.dateTimeServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.geoTaggingCharacteristicUuid
        }

        val currentlyEnabled = isGeoTaggingEnabled()
        if (currentlyEnabled == enabled) {
            Log.i(TAG, "Geo-tagging already ${if (enabled) "enabled" else "disabled"}, skipping")
            return
        }

        Log.i(TAG, "${if (enabled) "Enabling" else "Disabling"} geo-tagging")
        peripheral.write(
            characteristic = char,
            data = protocol.encodeGeoTaggingEnabled(enabled),
            writeType = WriteType.WithResponse,
        )
    }

    override suspend fun isGeoTaggingEnabled(): Boolean {
        if (!capabilities.supportsGeoTagging) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support geo-tagging"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.dateTimeServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.geoTaggingCharacteristicUuid
        }

        val data = peripheral.read(char)
        val enabled = protocol.decodeGeoTaggingEnabled(data)
        Log.i(TAG, "Geo-tagging is ${if (enabled) "enabled" else "disabled"}")
        return enabled
    }

    override suspend fun syncLocation(location: GpsLocation) {
        if (!capabilities.supportsLocationSync) {
            throw UnsupportedOperationException(
                "${camera.vendor.vendorName} cameras do not support location synchronization"
            )
        }

        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid == gattSpec.locationServiceUuid
        }
        val char = service.characteristics.first {
            it.characteristicUuid == gattSpec.locationCharacteristicUuid
        }

        val data = protocol.encodeLocation(location)
        Log.i(
            TAG,
            "Syncing location: ${protocol.decodeLocation(data)}",
        )
        peripheral.write(char, data, WriteType.WithResponse)
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun disconnect() {
        Log.i(TAG, "Disconnecting from ${camera.name}")
        peripheral.disconnect()
    }

    companion object {
        private const val TAG = "KableCameraConnection"
    }
}
