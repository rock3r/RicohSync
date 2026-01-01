package dev.sebastiano.ricohsync.devicesync

import android.util.Log
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.fakes.FakeLocationRepository
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLocationCollectorTest {

    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var testScope: TestScope
    private lateinit var locationCollector: DefaultLocationCollector

    private val testLocation =
        GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC")),
        )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        locationRepository = FakeLocationRepository()
        testScope = TestScope(UnconfinedTestDispatcher())

        locationCollector =
            DefaultLocationCollector(
                locationRepository = locationRepository,
                coroutineScope = testScope.backgroundScope,
            )
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
        unmockkStatic(Log::class)
    }

    @Test
    fun `initial state is not collecting`() =
        testScope.runTest {
            assertFalse(locationCollector.isCollecting.value)
            assertNull(locationCollector.locationUpdates.value)
            assertEquals(0, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `startCollecting starts location updates`() =
        testScope.runTest {
            locationCollector.startCollecting()
            advanceUntilIdle()

            assertTrue(locationCollector.isCollecting.value)
            assertTrue(locationRepository.startLocationUpdatesCalled)
        }

    @Test
    fun `stopCollecting stops location updates`() =
        testScope.runTest {
            locationCollector.startCollecting()
            advanceUntilIdle()

            locationCollector.stopCollecting()
            advanceUntilIdle()

            assertFalse(locationCollector.isCollecting.value)
            assertTrue(locationRepository.stopLocationUpdatesCalled)
        }

    @Test
    fun `location updates are forwarded`() =
        testScope.runTest {
            locationCollector.startCollecting()
            advanceUntilIdle()

            locationRepository.emit(testLocation)
            advanceUntilIdle()

            assertEquals(testLocation, locationCollector.locationUpdates.value)
        }

    @Test
    fun `registerDevice starts collecting if not already`() =
        testScope.runTest {
            assertFalse(locationCollector.isCollecting.value)

            locationCollector.registerDevice("device1")
            advanceUntilIdle()

            assertTrue(locationCollector.isCollecting.value)
            assertEquals(1, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `registerDevice multiple devices`() =
        testScope.runTest {
            locationCollector.registerDevice("device1")
            locationCollector.registerDevice("device2")
            locationCollector.registerDevice("device3")

            assertEquals(3, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `unregisterDevice decreases count`() =
        testScope.runTest {
            locationCollector.registerDevice("device1")
            locationCollector.registerDevice("device2")
            assertEquals(2, locationCollector.getRegisteredDeviceCount())

            locationCollector.unregisterDevice("device1")
            assertEquals(1, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `unregisterDevice stops collecting when no devices left`() =
        testScope.runTest {
            locationCollector.registerDevice("device1")
            advanceUntilIdle()
            assertTrue(locationCollector.isCollecting.value)

            locationCollector.unregisterDevice("device1")
            advanceUntilIdle()

            assertFalse(locationCollector.isCollecting.value)
            assertEquals(0, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `duplicate registrations are counted as single device`() =
        testScope.runTest {
            locationCollector.registerDevice("device1")
            locationCollector.registerDevice("device1") // Duplicate
            locationCollector.registerDevice("device1") // Duplicate

            // ConcurrentHashMap.newKeySet() prevents duplicates
            assertEquals(1, locationCollector.getRegisteredDeviceCount())
        }

    @Test
    fun `startCollecting is idempotent`() =
        testScope.runTest {
            locationCollector.startCollecting()
            locationCollector.startCollecting() // Should be ignored
            locationCollector.startCollecting() // Should be ignored
            advanceUntilIdle()

            assertTrue(locationCollector.isCollecting.value)
            // Only one call to start should have happened
        }
}
