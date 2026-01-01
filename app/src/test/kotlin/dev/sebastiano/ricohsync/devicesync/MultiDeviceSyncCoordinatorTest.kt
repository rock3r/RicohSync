package dev.sebastiano.ricohsync.devicesync

import android.util.Log
import dev.sebastiano.ricohsync.domain.model.DeviceConnectionState
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.PairedDevice
import dev.sebastiano.ricohsync.fakes.FakeCameraConnection
import dev.sebastiano.ricohsync.fakes.FakeCameraRepository
import dev.sebastiano.ricohsync.fakes.FakeCameraVendor
import dev.sebastiano.ricohsync.fakes.FakeLocationCollector
import dev.sebastiano.ricohsync.fakes.FakeVendorRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiDeviceSyncCoordinatorTest {

    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var locationCollector: FakeLocationCollector
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var testScope: TestScope
    private lateinit var coordinator: MultiDeviceSyncCoordinator

    private val testDevice1 = PairedDevice(
        macAddress = "00:11:22:33:44:55",
        name = "Test Camera 1",
        vendorId = "fake",
        isEnabled = true,
    )

    private val testDevice2 = PairedDevice(
        macAddress = "AA:BB:CC:DD:EE:FF",
        name = "Test Camera 2",
        vendorId = "fake",
        isEnabled = true,
    )

    private val testLocation = GpsLocation(
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
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        cameraRepository = FakeCameraRepository()
        locationCollector = FakeLocationCollector()
        vendorRegistry = FakeVendorRegistry()

        testScope = TestScope(UnconfinedTestDispatcher())

        coordinator = MultiDeviceSyncCoordinator(
            cameraRepository = cameraRepository,
            locationCollector = locationCollector,
            vendorRegistry = vendorRegistry,
            coroutineScope = testScope.backgroundScope,
            deviceNameProvider = { "Test Device RicohSync" },
        )
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
        unmockkStatic(Log::class)
    }

    @Test
    fun `initial state has no devices`() = testScope.runTest {
        assertEquals(emptyMap<String, DeviceConnectionState>(), coordinator.deviceStates.value)
        assertEquals(0, coordinator.getConnectedDeviceCount())
    }

    @Test
    fun `startDeviceSync transitions to Connecting state`() = testScope.runTest {
        cameraRepository.connectDelay = 1000L

        coordinator.startDeviceSync(testDevice1)

        assertEquals(
            DeviceConnectionState.Connecting,
            coordinator.getDeviceState(testDevice1.macAddress),
        )
    }

    @Test
    fun `startDeviceSync registers device for location updates`() = testScope.runTest {
        val connection = FakeCameraConnection(testDevice1.toTestCamera())
        cameraRepository.connectionToReturn = connection

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        assertTrue(locationCollector.registerDeviceCalls.contains(testDevice1.macAddress))
        assertEquals(1, locationCollector.getRegisteredDeviceCount())
    }

    @Test
    fun `multiple devices can be synced simultaneously`() = testScope.runTest {
        val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
        val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

        cameraRepository.connectionToReturn = connection1
        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        cameraRepository.connectionToReturn = connection2
        coordinator.startDeviceSync(testDevice2)
        advanceUntilIdle()

        assertEquals(2, locationCollector.getRegisteredDeviceCount())
        assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))
        assertTrue(coordinator.isDeviceConnected(testDevice2.macAddress))
    }

    @Test
    fun `location updates are synced to all connected devices`() = testScope.runTest {
        val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
        val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

        cameraRepository.connectionToReturn = connection1
        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        cameraRepository.connectionToReturn = connection2
        coordinator.startDeviceSync(testDevice2)
        advanceUntilIdle()

        // Emit a location
        locationCollector.emitLocation(testLocation)
        advanceUntilIdle()

        // Both devices should have received the location
        assertEquals(testLocation, connection1.lastSyncedLocation)
        assertEquals(testLocation, connection2.lastSyncedLocation)
    }

    @Test
    fun `stopDeviceSync disconnects device and updates state`() = testScope.runTest {
        val connection = FakeCameraConnection(testDevice1.toTestCamera())
        cameraRepository.connectionToReturn = connection

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()
        assertTrue(coordinator.isDeviceConnected(testDevice1.macAddress))

        coordinator.stopDeviceSync(testDevice1.macAddress)
        advanceUntilIdle()

        assertFalse(coordinator.isDeviceConnected(testDevice1.macAddress))
        assertEquals(
            DeviceConnectionState.Disconnected,
            coordinator.getDeviceState(testDevice1.macAddress),
        )
    }

    @Test
    fun `stopDeviceSync unregisters device from location updates`() = testScope.runTest {
        val connection = FakeCameraConnection(testDevice1.toTestCamera())
        cameraRepository.connectionToReturn = connection

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()
        assertEquals(1, locationCollector.getRegisteredDeviceCount())

        coordinator.stopDeviceSync(testDevice1.macAddress)
        advanceUntilIdle()

        assertTrue(locationCollector.unregisterDeviceCalls.contains(testDevice1.macAddress))
        assertEquals(0, locationCollector.getRegisteredDeviceCount())
    }

    @Test
    fun `stopAllDevices stops all syncs`() = testScope.runTest {
        val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
        val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

        cameraRepository.connectionToReturn = connection1
        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        cameraRepository.connectionToReturn = connection2
        coordinator.startDeviceSync(testDevice2)
        advanceUntilIdle()

        assertEquals(2, coordinator.getConnectedDeviceCount())

        coordinator.stopAllDevices()
        advanceUntilIdle()

        assertEquals(0, coordinator.getConnectedDeviceCount())
    }

    @Test
    fun `connection error updates state to Error`() = testScope.runTest {
        cameraRepository.connectException = RuntimeException("Connection failed")

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        val state = coordinator.getDeviceState(testDevice1.macAddress)
        assertTrue(state is DeviceConnectionState.Error)
        assertTrue((state as DeviceConnectionState.Error).message.contains("Connection failed"))
    }

    @Test
    fun `unknown vendor updates state to Error`() = testScope.runTest {
        vendorRegistry.clearVendors() // Remove all vendors

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        val state = coordinator.getDeviceState(testDevice1.macAddress)
        assertTrue(state is DeviceConnectionState.Error)
        assertTrue((state as DeviceConnectionState.Error).message.contains("Unknown camera vendor"))
        assertFalse(state.isRecoverable)
    }

    @Test
    fun `duplicate startDeviceSync calls are ignored`() = testScope.runTest {
        val connection = FakeCameraConnection(testDevice1.toTestCamera())
        cameraRepository.connectionToReturn = connection

        coordinator.startDeviceSync(testDevice1)
        coordinator.startDeviceSync(testDevice1) // Should be ignored
        advanceUntilIdle()

        assertEquals(1, cameraRepository.connectCallCount)
    }

    @Test
    fun `getConnectedDeviceCount returns correct count`() = testScope.runTest {
        val connection1 = FakeCameraConnection(testDevice1.toTestCamera())
        val connection2 = FakeCameraConnection(testDevice2.toTestCamera())

        assertEquals(0, coordinator.getConnectedDeviceCount())

        cameraRepository.connectionToReturn = connection1
        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()
        assertEquals(1, coordinator.getConnectedDeviceCount())

        cameraRepository.connectionToReturn = connection2
        coordinator.startDeviceSync(testDevice2)
        advanceUntilIdle()
        assertEquals(2, coordinator.getConnectedDeviceCount())
    }

    @Test
    fun `clearDeviceState removes device from states`() = testScope.runTest {
        val connection = FakeCameraConnection(testDevice1.toTestCamera())
        cameraRepository.connectionToReturn = connection

        coordinator.startDeviceSync(testDevice1)
        advanceUntilIdle()

        coordinator.stopDeviceSync(testDevice1.macAddress)
        advanceUntilIdle()

        // State should still exist as Disconnected
        assertEquals(
            DeviceConnectionState.Disconnected,
            coordinator.getDeviceState(testDevice1.macAddress),
        )

        // Clear the state
        coordinator.clearDeviceState(testDevice1.macAddress)

        // Now it should be gone (returns Disconnected as default)
        assertFalse(coordinator.deviceStates.value.containsKey(testDevice1.macAddress))
    }

    private fun PairedDevice.toTestCamera() = dev.sebastiano.ricohsync.domain.model.Camera(
        identifier = macAddress,
        name = name,
        macAddress = macAddress,
        vendor = FakeCameraVendor,
    )
}

