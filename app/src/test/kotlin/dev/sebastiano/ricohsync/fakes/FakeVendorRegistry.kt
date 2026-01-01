package dev.sebastiano.ricohsync.fakes

import dev.sebastiano.ricohsync.domain.vendor.CameraCapabilities
import dev.sebastiano.ricohsync.domain.vendor.CameraGattSpec
import dev.sebastiano.ricohsync.domain.vendor.CameraProtocol
import dev.sebastiano.ricohsync.domain.vendor.CameraVendor
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Fake implementation of [CameraVendorRegistry] for testing.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeVendorRegistry : CameraVendorRegistry {

    private val vendors = mutableListOf<CameraVendor>(FakeCameraVendor)

    override fun getAllVendors(): List<CameraVendor> = vendors.toList()

    override fun identifyVendor(deviceName: String?, serviceUuids: List<Uuid>): CameraVendor? {
        return vendors.firstOrNull { it.recognizesDevice(deviceName, serviceUuids) }
    }

    override fun getVendorById(vendorId: String): CameraVendor? {
        return vendors.find { it.vendorId == vendorId }
    }

    override fun getAllScanFilterUuids(): List<Uuid> {
        return vendors.flatMap { it.gattSpec.scanFilterServiceUuids }.distinct()
    }

    override fun getAllScanFilterDeviceNames(): List<String> {
        return vendors.flatMap { it.gattSpec.scanFilterDeviceNames }.distinct()
    }

    fun addVendor(vendor: CameraVendor) {
        vendors.add(vendor)
    }

    fun clearVendors() {
        vendors.clear()
    }
}

/**
 * Fake camera vendor for testing.
 */
@OptIn(ExperimentalUuidApi::class)
object FakeCameraVendor : CameraVendor {
    override val vendorId: String = "fake"
    override val vendorName: String = "Fake Camera"
    override val gattSpec: CameraGattSpec = FakeGattSpec
    override val protocol: CameraProtocol = FakeProtocol

    override fun recognizesDevice(deviceName: String?, serviceUuids: List<Uuid>): Boolean {
        return deviceName?.contains("Fake", ignoreCase = true) == true ||
            serviceUuids.any { it == FakeGattSpec.scanFilterServiceUuids.first() }
    }

    override fun getCapabilities(): CameraCapabilities = CameraCapabilities(
        supportsFirmwareVersion = true,
        supportsDeviceName = true,
        supportsDateTimeSync = true,
        supportsGeoTagging = true,
        supportsLocationSync = true,
    )
}

@OptIn(ExperimentalUuidApi::class)
object FakeGattSpec : CameraGattSpec {
    private val fakeUuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

    override val scanFilterServiceUuids: List<Uuid> = listOf(fakeUuid)
    override val firmwareServiceUuid: Uuid = fakeUuid
    override val firmwareVersionCharacteristicUuid: Uuid = fakeUuid
    override val deviceNameServiceUuid: Uuid = fakeUuid
    override val deviceNameCharacteristicUuid: Uuid = fakeUuid
    override val dateTimeServiceUuid: Uuid = fakeUuid
    override val dateTimeCharacteristicUuid: Uuid = fakeUuid
    override val geoTaggingCharacteristicUuid: Uuid = fakeUuid
    override val locationServiceUuid: Uuid = fakeUuid
    override val locationCharacteristicUuid: Uuid = fakeUuid
}

object FakeProtocol : CameraProtocol {
    override fun encodeDateTime(dateTime: ZonedDateTime): ByteArray = byteArrayOf()
    override fun decodeDateTime(bytes: ByteArray): String = "decoded-datetime"
    override fun encodeLocation(location: GpsLocation): ByteArray = byteArrayOf()
    override fun decodeLocation(bytes: ByteArray): String = "decoded-location"
    override fun encodeGeoTaggingEnabled(enabled: Boolean): ByteArray = byteArrayOf(if (enabled) 1 else 0)
    override fun decodeGeoTaggingEnabled(bytes: ByteArray): Boolean = bytes.firstOrNull() == 1.toByte()
}

