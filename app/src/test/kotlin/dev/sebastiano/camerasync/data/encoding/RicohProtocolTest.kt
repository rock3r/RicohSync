package dev.sebastiano.camerasync.data.encoding

import dev.sebastiano.camerasync.domain.model.GpsLocation
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RicohProtocolTest {

    @Test
    fun `encodeDateTime produces correct byte array size`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        assertEquals(RicohProtocol.DATE_TIME_SIZE, encoded.size)
    }

    @Test
    fun `encodeDateTime and decodeDateTime are inverse operations`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        val decoded = RicohProtocol.decodeDateTime(encoded)

        assertEquals(2024, decoded.year)
        assertEquals(12, decoded.month)
        assertEquals(25, decoded.day)
        assertEquals(14, decoded.hour)
        assertEquals(30, decoded.minute)
        assertEquals(45, decoded.second)
    }

    @Test
    fun `encodeDateTime handles year boundary correctly`() {
        val newYearEve = ZonedDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC"))
        val newYear = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))

        val decodedEve = RicohProtocol.decodeDateTime(RicohProtocol.encodeDateTime(newYearEve))
        val decodedNew = RicohProtocol.decodeDateTime(RicohProtocol.encodeDateTime(newYear))

        assertEquals(2023, decodedEve.year)
        assertEquals(12, decodedEve.month)
        assertEquals(31, decodedEve.day)
        assertEquals(23, decodedEve.hour)
        assertEquals(59, decodedEve.minute)
        assertEquals(59, decodedEve.second)

        assertEquals(2024, decodedNew.year)
        assertEquals(1, decodedNew.month)
        assertEquals(1, decodedNew.day)
        assertEquals(0, decodedNew.hour)
        assertEquals(0, decodedNew.minute)
        assertEquals(0, decodedNew.second)
    }

    @Test
    fun `encodeDateTime year is little-endian`() {
        // Year 2024 = 0x07E8
        // Little-endian: E8 07
        val dateTime = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)

        assertEquals(0xE8.toByte(), encoded[0])
        assertEquals(0x07.toByte(), encoded[1])
    }

    @Test
    fun `decodeDateTime throws on insufficient data`() {
        val tooShort = ByteArray(5)
        assertThrows(IllegalArgumentException::class.java) {
            RicohProtocol.decodeDateTime(tooShort)
        }
    }

    @Test
    fun `encodeLocation produces correct byte array size`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )
        val encoded = RicohProtocol.encodeLocation(location)
        assertEquals(RicohProtocol.LOCATION_SIZE, encoded.size)
    }

    @Test
    fun `encodeLocation and decodeLocation are inverse operations`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.5,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )

        val encoded = RicohProtocol.encodeLocation(location)
        val decoded = RicohProtocol.decodeLocation(encoded)

        assertEquals(37.7749, decoded.latitude, 0.0001)
        assertEquals(-122.4194, decoded.longitude, 0.0001)
        assertEquals(10.5, decoded.altitude, 0.0001)
        assertEquals(2024, decoded.dateTime.year)
        assertEquals(12, decoded.dateTime.month)
        assertEquals(25, decoded.dateTime.day)
        assertEquals(14, decoded.dateTime.hour)
        assertEquals(30, decoded.dateTime.minute)
        assertEquals(45, decoded.dateTime.second)
    }

    @Test
    fun `encodeLocation handles extreme coordinates`() {
        // North Pole
        val northPole =
            GpsLocation(
                latitude = 90.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedNorth = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(northPole))
        assertEquals(90.0, decodedNorth.latitude, 0.0001)

        // South Pole
        val southPole =
            GpsLocation(
                latitude = -90.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedSouth = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(southPole))
        assertEquals(-90.0, decodedSouth.latitude, 0.0001)

        // International Date Line
        val dateLine =
            GpsLocation(
                latitude = 0.0,
                longitude = 180.0,
                altitude = 0.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decodedDateLine = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(dateLine))
        assertEquals(180.0, decodedDateLine.longitude, 0.0001)
    }

    @Test
    fun `encodeLocation handles negative altitude`() {
        // Dead Sea - below sea level
        val deadSea =
            GpsLocation(
                latitude = 31.5,
                longitude = 35.5,
                altitude = -430.0,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decoded = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(deadSea))
        assertEquals(-430.0, decoded.altitude, 0.0001)
    }

    @Test
    fun `encodeLocation handles high altitude`() {
        // Mount Everest
        val everest =
            GpsLocation(
                latitude = 27.9881,
                longitude = 86.9250,
                altitude = 8848.86,
                timestamp = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")),
            )
        val decoded = RicohProtocol.decodeLocation(RicohProtocol.encodeLocation(everest))
        assertEquals(8848.86, decoded.altitude, 0.01)
    }

    @Test
    fun `decodeLocation throws on insufficient data`() {
        val tooShort = ByteArray(30)
        assertThrows(IllegalArgumentException::class.java) {
            RicohProtocol.decodeLocation(tooShort)
        }
    }

    @Test
    fun `formatDateTimeHex produces expected format`() {
        val dateTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC"))
        val encoded = RicohProtocol.encodeDateTime(dateTime)
        val hex = RicohProtocol.formatDateTimeHex(encoded)

        // Should have 6 underscore-separated segments
        assertEquals(6, hex.split("_").size)
    }

    @Test
    fun `formatLocationHex produces expected format`() {
        val location =
            GpsLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.0,
                timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 45, 0, ZoneId.of("UTC")),
            )
        val encoded = RicohProtocol.encodeLocation(location)
        val hex = RicohProtocol.formatLocationHex(encoded)

        // Should have 10 underscore-separated segments
        assertEquals(10, hex.split("_").size)
    }

    @Test
    fun `DecodedDateTime toString formats correctly`() {
        val decoded = DecodedDateTime(2024, 1, 5, 9, 3, 7)
        assertEquals("2024-01-05 09:03:07", decoded.toString())
    }

    @Test
    fun `DecodedLocation toString formats correctly`() {
        val decoded =
            DecodedLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.5,
                dateTime = DecodedDateTime(2024, 12, 25, 14, 30, 45),
            )
        val str = decoded.toString()
        assert(str.contains("37.7749"))
        assert(str.contains("-122.4194"))
        assert(str.contains("10.5"))
        assert(str.contains("2024-12-25 14:30:45"))
    }
}
