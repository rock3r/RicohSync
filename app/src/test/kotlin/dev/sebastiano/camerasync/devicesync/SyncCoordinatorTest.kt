package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.CameraSyncApp
import dev.sebastiano.camerasync.domain.model.Camera
import dev.sebastiano.camerasync.domain.model.GpsLocation
import dev.sebastiano.camerasync.domain.model.SyncState
import dev.sebastiano.camerasync.fakes.FakeCameraConnection
import dev.sebastiano.camerasync.fakes.FakeCameraRepository
import dev.sebastiano.camerasync.fakes.FakeKhronicleLogger
import dev.sebastiano.camerasync.fakes.FakeLocationRepository
import dev.sebastiano.camerasync.fakes.FakePairedDevicesRepository
import dev.sebastiano.camerasync.vendors.ricoh.RicohCameraVendor
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
class SyncCoordinatorTest {

    private lateinit var cameraRepository: FakeCameraRepository
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var cameraConnection: FakeCameraConnection
    private lateinit var testScope: TestScope
    private lateinit var syncCoordinator: SyncCoordinator

    private val testCamera =
        Camera(
            identifier = "00:11:22:33:44:55",
            name = "GR IIIx",
            macAddress = "00:11:22:33:44:55",
            vendor = RicohCameraVendor,
        )

    private val testLocation =
        GpsLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 10.0,
            timestamp = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC")),
        )

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        CameraSyncApp.initializeLogging(FakeKhronicleLogger)

        cameraRepository = FakeCameraRepository()
        locationRepository = FakeLocationRepository()
        pairedDevicesRepository = FakePairedDevicesRepository()
        cameraConnection = FakeCameraConnection(testCamera)
        cameraRepository.connectionToReturn = cameraConnection

        testScope = TestScope(UnconfinedTestDispatcher())

        syncCoordinator =
            SyncCoordinator(
                cameraRepository = cameraRepository,
                locationRepository = locationRepository,
                pairedDevicesRepository = pairedDevicesRepository,
                coroutineScope = testScope.backgroundScope,
                deviceNameProvider = { "Test Device CameraSync" },
            )
    }

    @After
    fun tearDown() {
        // Cancel all background jobs in the test scope
        testScope.backgroundScope.cancel()
    }

    @Test
    fun `initial state is Idle`() =
        testScope.runTest { assertEquals(SyncState.Idle, syncCoordinator.state.value) }

    @Test
    fun `startSync transitions to Connecting state`() =
        testScope.runTest {
            cameraRepository.connectDelay = 1000L

            syncCoordinator.startSync(testCamera)

            assertEquals(SyncState.Connecting(testCamera), syncCoordinator.state.value)
        }

    @Test
    fun `isSyncing returns true when sync is in progress`() =
        testScope.runTest {
            assertFalse(syncCoordinator.isSyncing())

            syncCoordinator.startSync(testCamera)

            assertTrue(syncCoordinator.isSyncing())
        }

    @Test
    fun `startSync performs initial setup on camera`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            assertTrue(cameraConnection.readDateTimeCalled)
            assertTrue(cameraConnection.readFirmwareVersionCalled)
            assertEquals("Test Device CameraSync", cameraConnection.pairedDeviceName)
            assertTrue(cameraConnection.syncedDateTime != null)
            assertTrue(cameraConnection.geoTaggingEnabled)
        }

    @Test
    fun `startSync transitions to Syncing state after setup`() =
        testScope.runTest {
            cameraConnection.firmwareVersion = "1.0.0"

            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            val state = syncCoordinator.state.value
            assertTrue(state is SyncState.Syncing)
            assertEquals(testCamera, (state as SyncState.Syncing).camera)
            assertEquals("1.0.0", state.firmwareVersion)
        }

    @Test
    fun `startSync starts location updates`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            assertTrue(locationRepository.startLocationUpdatesCalled)
        }

    @Test
    fun `location updates are synced to camera`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            locationRepository.emit(testLocation)
            advanceUntilIdle()

            assertEquals(testLocation, cameraConnection.lastSyncedLocation)
        }

    @Test
    fun `stopSync transitions to Stopped state`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            syncCoordinator.stopSync()
            advanceUntilIdle()

            assertEquals(SyncState.Stopped, syncCoordinator.state.value)
        }

    @Test
    fun `stopSync disconnects from camera`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            syncCoordinator.stopSync()
            advanceUntilIdle()

            assertTrue(cameraConnection.disconnectCalled)
        }

    @Test
    fun `stopSync stops location updates`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            syncCoordinator.stopSync()
            advanceUntilIdle()

            assertTrue(locationRepository.stopLocationUpdatesCalled)
        }

    @Test
    fun `stopSync clears syncing flag`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()
            assertTrue(syncCoordinator.isSyncing())

            syncCoordinator.stopSync()
            advanceUntilIdle()

            assertFalse(syncCoordinator.isSyncing())
        }

    @Test
    fun `connection error transitions to Disconnected state`() =
        testScope.runTest {
            cameraRepository.connectException = RuntimeException("Connection failed")

            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            val state = syncCoordinator.state.value
            assertTrue(state is SyncState.Disconnected)
            assertEquals(testCamera, (state as SyncState.Disconnected).camera)
        }

    @Test
    fun `duplicate startSync calls are ignored`() =
        testScope.runTest {
            // We can't easily count calls with current fake without adding a counter
            // Let's add a counter to connect call in FakeCameraRepository
            syncCoordinator.startSync(testCamera)
            syncCoordinator.startSync(testCamera) // Should be ignored

            advanceUntilIdle()

            assertEquals(1, cameraRepository.connectCallCount)
        }

    @Test
    fun `findAndSync locates camera and starts sync`() =
        testScope.runTest {
            cameraRepository.cameraToReturn = testCamera

            syncCoordinator.findAndSync(testCamera.macAddress)
            advanceUntilIdle()

            assertEquals(1, cameraRepository.connectCallCount)
            assertEquals(testCamera, cameraConnection.camera)
        }

    @Test
    fun `syncing state includes firmware version`() =
        testScope.runTest {
            cameraConnection.firmwareVersion = "2.5.1"

            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            val state = syncCoordinator.state.value as SyncState.Syncing
            assertEquals("2.5.1", state.firmwareVersion)
        }

    @Test
    fun `syncing state updates with location sync info`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()

            locationRepository.emit(testLocation)
            advanceUntilIdle()

            val state = syncCoordinator.state.value as SyncState.Syncing
            assertEquals(testLocation, state.lastSyncInfo?.location)
        }

    @Test
    fun `camera disconnection transitions to Disconnected state`() =
        testScope.runTest {
            syncCoordinator.startSync(testCamera)
            advanceUntilIdle()
            assertTrue(syncCoordinator.isSyncing())

            // Simulate disconnection
            cameraConnection.setConnected(false)
            advanceUntilIdle()

            assertFalse(syncCoordinator.isSyncing())
            val state = syncCoordinator.state.value
            assertTrue(state is SyncState.Disconnected)
            assertEquals(testCamera, (state as SyncState.Disconnected).camera)
        }
}
