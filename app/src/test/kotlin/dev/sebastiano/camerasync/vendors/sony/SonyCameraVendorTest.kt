package dev.sebastiano.camerasync.vendors.sony

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class SonyCameraVendorTest {

    @Test
    fun `vendorId is sony`() {
        assertEquals("sony", SonyCameraVendor.vendorId)
    }

    @Test
    fun `vendorName is Sony`() {
        assertEquals("Sony", SonyCameraVendor.vendorName)
    }

    @Test
    fun `recognizes device with remote control service UUID`() {
        val serviceUuids = listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID)
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", serviceUuids))
    }

    @Test
    fun `recognizes device regardless of device name`() {
        val serviceUuids = listOf(SonyGattSpec.REMOTE_CONTROL_SERVICE_UUID)
        assertTrue(SonyCameraVendor.recognizesDevice(null, serviceUuids))
        assertTrue(SonyCameraVendor.recognizesDevice("", serviceUuids))
        assertTrue(SonyCameraVendor.recognizesDevice("Unknown Camera", serviceUuids))
    }

    @Test
    fun `does not recognize device without remote control service UUID`() {
        val serviceUuids = listOf(Uuid.parse("00001800-0000-1000-8000-00805f9b34fb"))
        assertFalse(SonyCameraVendor.recognizesDevice("ILCE-7M4", serviceUuids))
    }

    @Test
    fun `recognizes device by name pattern even without service UUID`() {
        // Sony cameras with ILCE- prefix should be recognized even if service UUID isn't advertised
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M4", emptyList()))
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-7M3", emptyList()))
        assertTrue(SonyCameraVendor.recognizesDevice("ILCE-9", emptyList()))
    }

    @Test
    fun `does not recognize device without service UUID or recognized name`() {
        assertFalse(SonyCameraVendor.recognizesDevice("Unknown Camera", emptyList()))
        assertFalse(SonyCameraVendor.recognizesDevice(null, emptyList()))
    }

    @Test
    fun `capabilities indicate firmware version support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsFirmwareVersion)
    }

    @Test
    fun `capabilities indicate no device name support`() {
        assertFalse(SonyCameraVendor.getCapabilities().supportsDeviceName)
    }

    @Test
    fun `capabilities indicate date time sync support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsDateTimeSync)
    }

    @Test
    fun `capabilities indicate no geo tagging support`() {
        assertFalse(SonyCameraVendor.getCapabilities().supportsGeoTagging)
    }

    @Test
    fun `capabilities indicate location sync support`() {
        assertTrue(SonyCameraVendor.getCapabilities().supportsLocationSync)
    }

    @Test
    fun `gattSpec is SonyGattSpec`() {
        assertEquals(SonyGattSpec, SonyCameraVendor.gattSpec)
    }

    @Test
    fun `protocol is SonyProtocol`() {
        assertEquals(SonyProtocol, SonyCameraVendor.protocol)
    }
}
