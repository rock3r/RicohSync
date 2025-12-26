package dev.sebastiano.ricohsync.vendors.sony

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class SonyGattSpecTest {

    @Test
    fun `remote control service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000FF00-FF00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
        )
    }

    @Test
    fun `location service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD00-DD00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_SERVICE_UUID,
        )
    }

    @Test
    fun `location status notify characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD01-DD01-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_STATUS_NOTIFY_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location data write characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD11-DD11-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location config read characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD21-DD21-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_CONFIG_READ_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location lock characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD30-DD30-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_LOCK_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `location enable characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000DD31-DD31-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.LOCATION_ENABLE_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `pairing service UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000EE00-EE00-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.PAIRING_SERVICE_UUID,
        )
    }

    @Test
    fun `pairing characteristic UUID matches documentation`() {
        assertEquals(
            Uuid.parse("8000EE01-EE01-FFFF-FFFF-FFFFFFFFFFFF"),
            SonyGattSpec.PAIRING_CHARACTERISTIC_UUID,
        )
    }

    @Test
    fun `scan filter uses remote control service UUID`() {
        assertEquals(1, SonyGattSpec.scanFilterServiceUuids.size)
        assertEquals(
            SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID,
            SonyGattSpec.scanFilterServiceUuids.first(),
        )
    }

    @Test
    fun `dateTime uses location service and data write characteristic`() {
        assertEquals(SonyGattSpec.LOCATION_SERVICE_UUID, SonyGattSpec.dateTimeServiceUuid)
        assertEquals(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID, SonyGattSpec.dateTimeCharacteristicUuid)
    }

    @Test
    fun `location uses location service and data write characteristic`() {
        assertEquals(SonyGattSpec.LOCATION_SERVICE_UUID, SonyGattSpec.locationServiceUuid)
        assertEquals(SonyGattSpec.LOCATION_DATA_WRITE_CHARACTERISTIC_UUID, SonyGattSpec.locationCharacteristicUuid)
    }

    @Test
    fun `geoTagging characteristic is null`() {
        assertNull(SonyGattSpec.geoTaggingCharacteristicUuid)
    }

    @Test
    fun `firmware uses standard Device Information Service`() {
        assertEquals(
            Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.firmwareServiceUuid,
        )
        assertEquals(
            Uuid.parse("00002a26-0000-1000-8000-00805f9b34fb"),
            SonyGattSpec.firmwareVersionCharacteristicUuid,
        )
    }

    @Test
    fun `device name is not supported`() {
        assertNull(SonyGattSpec.deviceNameServiceUuid)
        assertNull(SonyGattSpec.deviceNameCharacteristicUuid)
    }
}
