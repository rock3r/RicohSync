package dev.sebastiano.ricohsync.vendors.sony

import dev.sebastiano.ricohsync.domain.model.GpsLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyProtocolTest {

    @Test
    fun `encodeDateTime produces 95 bytes with timezone`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        assertEquals(95, encoded.size)
    }

    @Test
    fun `encodeDateTime sets correct payload length`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        // Payload length should be 93 (95 - 2 bytes for length field)
        assertEquals(93, buffer.short.toInt())
    }

    @Test
    fun `encodeDateTime sets correct fixed header`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Bytes 2-4: Fixed header 0x08 0x02 0xFC
        assertEquals(0x08.toByte(), encoded[2])
        assertEquals(0x02.toByte(), encoded[3])
        assertEquals(0xFC.toByte(), encoded[4])
    }

    @Test
    fun `encodeDateTime sets timezone flag to include`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Byte 5: Timezone flag (0x03 = include)
        assertEquals(0x03.toByte(), encoded[5])
    }

    @Test
    fun `encodeDateTime sets correct fixed data bytes`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        // Bytes 6-10: Fixed data 0x00 0x00 0x10 0x10 0x10
        assertEquals(0x00.toByte(), encoded[6])
        assertEquals(0x00.toByte(), encoded[7])
        assertEquals(0x10.toByte(), encoded[8])
        assertEquals(0x10.toByte(), encoded[9])
        assertEquals(0x10.toByte(), encoded[10])
    }

    @Test
    fun `encodeDateTime sets zero coordinates for time-only`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)
        assertEquals(0, buffer.int) // Latitude
        assertEquals(0, buffer.int) // Longitude
    }

    @Test
    fun `encodeDateTime sets correct UTC timestamp`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(19)
        assertEquals(2024, buffer.short.toInt()) // Year
        assertEquals(12, buffer.get().toInt()) // Month
        assertEquals(25, buffer.get().toInt()) // Day
        assertEquals(14, buffer.get().toInt()) // Hour
        assertEquals(30, buffer.get().toInt()) // Minute
        assertEquals(45, buffer.get().toInt()) // Second
    }

    @Test
    fun `encodeDateTime converts local time to UTC`() {
        // 14:30 in UTC+8 should become 06:30 UTC
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(19)
        assertEquals(2024, buffer.short.toInt())
        assertEquals(12, buffer.get().toInt())
        assertEquals(25, buffer.get().toInt())
        assertEquals(6, buffer.get().toInt()) // 14 - 8 = 6
        assertEquals(30, buffer.get().toInt())
        assertEquals(45, buffer.get().toInt())
    }

    @Test
    fun `encodeDateTime sets correct timezone offset`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.ofHours(8))
        val encoded = SonyProtocol.encodeDateTime(dateTime)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(91)
        // UTC+8 = 480 minutes
        assertEquals(480, buffer.short.toInt())
        assertEquals(0, buffer.short.toInt()) // DST offset
    }

    @Test
    fun `encodeLocation produces 95 bytes`() {
        val location = GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
        )
        val encoded = SonyProtocol.encodeLocation(location)
        assertEquals(95, encoded.size)
    }

    @Test
    fun `encodeLocation sets correct coordinates`() {
        val location = GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
        )
        val encoded = SonyProtocol.encodeLocation(location)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)

        // Coordinates scaled by 10,000,000
        val expectedLat = (37.7749 * 10_000_000).toInt()
        val expectedLon = (-122.4194 * 10_000_000).toInt()

        assertEquals(expectedLat, buffer.int)
        assertEquals(expectedLon, buffer.int)
    }

    @Test
    fun `encodeLocation handles negative coordinates`() {
        val location = GpsLocation(
            latitude = -33.8688,
            longitude = 151.2093,
            altitude = 0.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
        )
        val encoded = SonyProtocol.encodeLocation(location)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        buffer.position(11)

        val expectedLat = (-33.8688 * 10_000_000).toInt()
        val expectedLon = (151.2093 * 10_000_000).toInt()

        assertEquals(expectedLat, buffer.int)
        assertEquals(expectedLon, buffer.int)
    }

    @Test
    fun `decodeLocation correctly decodes encoded location`() {
        val location = GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 0.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC),
        )
        val encoded = SonyProtocol.encodeLocation(location)
        val decoded = SonyProtocol.decodeLocation(encoded)

        assertTrue("Decoded string should contain latitude", decoded.contains("37.7749"))
        assertTrue("Decoded string should contain longitude", decoded.contains("-122.4194"))
        assertTrue("Decoded string should contain date", decoded.contains("2024-12-25"))
    }

    @Test
    fun `decodeDateTime correctly decodes encoded time`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneOffset.UTC)
        val encoded = SonyProtocol.encodeDateTime(dateTime)
        val decoded = SonyProtocol.decodeDateTime(encoded)

        assertTrue("Decoded string should contain date", decoded.contains("2024-12-25"))
        assertTrue("Decoded string should contain time", decoded.contains("14:30:45"))
    }

    @Test
    fun `decodeDateTime returns error for short data`() {
        val decoded = SonyProtocol.decodeDateTime(ByteArray(10))
        assertTrue("Should indicate invalid data", decoded.contains("Invalid data"))
    }

    @Test
    fun `decodeLocation returns error for short data`() {
        val decoded = SonyProtocol.decodeLocation(ByteArray(10))
        assertTrue("Should indicate invalid data", decoded.contains("Invalid data"))
    }

    @Test
    fun `encodeLocationPacket without timezone produces 91 bytes`() {
        val packet = SonyProtocol.encodeLocationPacket(
            latitude = 0.0,
            longitude = 0.0,
            dateTime = ZonedDateTime.now(ZoneOffset.UTC),
            includeTimezone = false,
        )
        assertEquals(91, packet.size)
    }

    @Test
    fun `encodeLocationPacket without timezone sets correct flag`() {
        val packet = SonyProtocol.encodeLocationPacket(
            latitude = 0.0,
            longitude = 0.0,
            dateTime = ZonedDateTime.now(ZoneOffset.UTC),
            includeTimezone = false,
        )
        // Byte 5: Timezone flag (0x00 = omit)
        assertEquals(0x00.toByte(), packet[5])
    }

    @Test
    fun `parseConfigRequiresTimezone returns true when bit 2 is set`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x06, 0x00)
        assertTrue(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false when bit 2 is not set`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x02, 0x00)
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false for short data`() {
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun `createStatusNotifyEnable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x01)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyEnable())
    }

    @Test
    fun `createStatusNotifyDisable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x00)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyDisable())
    }

    @Test
    fun `createPairingInit returns correct bytes`() {
        val expected = byteArrayOf(0x06, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, SonyProtocol.createPairingInit())
    }

    @Test
    fun `encodeGeoTaggingEnabled returns empty array`() {
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(true).size)
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(false).size)
    }

    @Test
    fun `decodeGeoTaggingEnabled returns false`() {
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf(0x01)))
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf()))
    }
}
