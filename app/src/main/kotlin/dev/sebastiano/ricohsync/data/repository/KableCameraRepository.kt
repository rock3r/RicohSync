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
import dev.sebastiano.ricohsync.ble.RicohGattSpec
import dev.sebastiano.ricohsync.data.encoding.RicohProtocol
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import java.time.ZonedDateTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "KableCameraRepository"

/**
 * Implementation of [CameraRepository] using the Kable BLE library.
 */
class KableCameraRepository : CameraRepository {

    @OptIn(ObsoleteKableApi::class)
    private val scanner by lazy {
        com.juul.kable.Scanner {
            filters {
                match { services = listOf(RicohGattSpec.SCAN_FILTER_SERVICE_UUID) }
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

    override val discoveredCameras: Flow<RicohCamera>
        get() = scanner.advertisements.map { it.toRicohCamera() }

    override fun startScanning() {
        // Scanner is lazy and starts when advertisements flow is collected
        Log.i(TAG, "Starting camera scan")
    }

    override fun stopScanning() {
        Log.i(TAG, "Stopping camera scan")
        // Scanner stops when the flow collection is cancelled
    }

    override fun findCameraByMacAddress(macAddress: String): Flow<RicohCamera> {
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
        return scanner.advertisements.map { it.toRicohCamera() }
    }

    override suspend fun connect(camera: RicohCamera): CameraConnection {
        // We need to scan for the device first to get the Advertisement
        // This is a limitation of Kable - we need the Advertisement to create a Peripheral
        val scanner = com.juul.kable.Scanner {
            @OptIn(ObsoleteKableApi::class)
            filters {
                match { address = camera.macAddress }
            }
        }

        var advertisement: Advertisement? = null
        scanner.advertisements.collect {
            advertisement = it
            return@collect
        }

        val peripheral = Peripheral(advertisement!!) {
            logging {
                level = Logging.Level.Events
                engine = SystemLogEngine
                identifier = "RicohCamera"
            }
        }

        Log.i(TAG, "Connecting to ${camera.name}...")
        peripheral.connect()
        Log.i(TAG, "Connected to ${camera.name}")

        return KableCameraConnection(camera, peripheral)
    }

    private fun Advertisement.toRicohCamera(): RicohCamera = RicohCamera(
        identifier = identifier,
        name = peripheralName,
        macAddress = identifier, // On Android, identifier is the MAC address
    )
}

/**
 * Implementation of [CameraConnection] using a Kable Peripheral.
 */
internal class KableCameraConnection(
    override val camera: RicohCamera,
    private val peripheral: Peripheral,
) : CameraConnection {

    override suspend fun readFirmwareVersion(): String {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.Firmware.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.Firmware.VERSION_CHARACTERISTIC_UUID.toString()
        }

        val firmwareBytes = peripheral.read(char)
        val version = firmwareBytes.decodeToString().trimEnd(Char(0))
        Log.i(TAG, "Firmware version: $version")
        return version
    }

    override suspend fun setPairedDeviceName(name: String) {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.DeviceName.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.DeviceName.NAME_CHARACTERISTIC_UUID.toString()
        }

        Log.i(TAG, "Setting paired device name: $name")
        peripheral.write(
            characteristic = char,
            data = name.encodeToByteArray(),
            writeType = WriteType.WithResponse,
        )
    }

    override suspend fun syncDateTime(dateTime: ZonedDateTime) {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.DateTime.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID.toString()
        }

        val data = RicohProtocol.encodeDateTime(dateTime)
        Log.i(
            TAG,
            "Syncing date/time:\n" +
                "Raw: ${RicohProtocol.formatDateTimeHex(data)}\n" +
                "Decoded: ${RicohProtocol.decodeDateTime(data)}",
        )
        peripheral.write(char, data, WriteType.WithResponse)
    }

    override suspend fun readDateTime(): ByteArray {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.DateTime.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.DateTime.DATE_TIME_CHARACTERISTIC_UUID.toString()
        }

        val data = peripheral.read(char)
        Log.i(
            TAG,
            "Read camera date/time:\n" +
                "Raw: ${RicohProtocol.formatDateTimeHex(data)}\n" +
                "Decoded: ${RicohProtocol.decodeDateTime(data)}",
        )
        return data
    }

    override suspend fun setGeoTaggingEnabled(enabled: Boolean) {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.DateTime.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID.toString()
        }

        val currentlyEnabled = isGeoTaggingEnabled()
        if (currentlyEnabled == enabled) {
            Log.i(TAG, "Geo-tagging already ${if (enabled) "enabled" else "disabled"}, skipping")
            return
        }

        Log.i(TAG, "${if (enabled) "Enabling" else "Disabling"} geo-tagging")
        peripheral.write(
            characteristic = char,
            data = ByteArray(1) { if (enabled) 1 else 0 },
            writeType = WriteType.WithResponse,
        )
    }

    override suspend fun isGeoTaggingEnabled(): Boolean {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.DateTime.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.DateTime.GEO_TAGGING_CHARACTERISTIC_UUID.toString()
        }

        val data = peripheral.read(char)
        val enabled = data.first() == 1.toByte()
        Log.i(TAG, "Geo-tagging is ${if (enabled) "enabled" else "disabled"}")
        return enabled
    }

    override suspend fun syncLocation(location: GpsLocation) {
        val service = peripheral.services.value.orEmpty().first {
            it.serviceUuid.toString() == RicohGattSpec.Location.SERVICE_UUID.toString()
        }
        val char = service.characteristics.first {
            it.characteristicUuid.toString() ==
                RicohGattSpec.Location.LOCATION_CHARACTERISTIC_UUID.toString()
        }

        val data = RicohProtocol.encodeLocation(location)
        Log.i(
            TAG,
            "Syncing location:\n" +
                "Raw: ${RicohProtocol.formatLocationHex(data)}\n" +
                "Decoded: ${RicohProtocol.decodeLocation(data)}",
        )
        peripheral.write(char, data, WriteType.WithResponse)
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun disconnect() {
        Log.i(TAG, "Disconnecting from ${camera.name}")
        peripheral.disconnect()
        peripheral.cancel("Disconnecting")
    }

    companion object {
        private const val TAG = "KableCameraConnection"
    }
}
