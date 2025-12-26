package dev.sebastiano.ricohsync.devicesync

import android.util.Log
import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import dev.sebastiano.ricohsync.domain.model.SyncState
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.vendors.ricoh.RicohCameraVendor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    private lateinit var cameraRepository: CameraRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var cameraConnection: CameraConnection
    private lateinit var testScope: TestScope
    private lateinit var syncCoordinator: SyncCoordinator

    private val testCamera = RicohCamera(
        identifier = "00:11:22:33:44:55",
        name = "GR IIIx",
        macAddress = "00:11:22:33:44:55",
        vendor = RicohCameraVendor,
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
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        cameraRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        cameraConnection = mockk(relaxed = true)

        testScope = TestScope(UnconfinedTestDispatcher())

        every { locationRepository.locationUpdates } returns MutableStateFlow(null)

        syncCoordinator = SyncCoordinator(
            cameraRepository = cameraRepository,
            locationRepository = locationRepository,
            coroutineScope = testScope.backgroundScope,
            deviceNameProvider = { "Test Device RicohSync" },
        )
    }

    @After
    fun tearDown() {
        // Cancel all background jobs in the test scope
        testScope.backgroundScope.cancel()
        unmockkStatic(Log::class)
    }

    @Test
    fun `initial state is Idle`() = testScope.runTest {
        assertEquals(SyncState.Idle, syncCoordinator.state.value)
    }

    @Test
    fun `startSync transitions to Connecting state`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } coAnswers {
            // Simulate connection delay
            delay(1000)
            cameraConnection
        }

        syncCoordinator.startSync(testCamera)

        assertEquals(SyncState.Connecting(testCamera), syncCoordinator.state.value)
    }

    @Test
    fun `isSyncing returns true when sync is in progress`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        assertFalse(syncCoordinator.isSyncing())

        syncCoordinator.startSync(testCamera)

        assertTrue(syncCoordinator.isSyncing())
    }

    @Test
    fun `startSync performs initial setup on camera`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        coVerify { cameraConnection.readDateTime() }
        coVerify { cameraConnection.readFirmwareVersion() }
        coVerify { cameraConnection.setPairedDeviceName("Test Device RicohSync") }
        coVerify { cameraConnection.syncDateTime(any()) }
        coVerify { cameraConnection.setGeoTaggingEnabled(true) }
    }

    @Test
    fun `startSync transitions to Syncing state after setup`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        val state = syncCoordinator.state.value
        assertTrue(state is SyncState.Syncing)
        assertEquals(testCamera, (state as SyncState.Syncing).camera)
        assertEquals("1.0.0", state.firmwareVersion)
    }

    @Test
    fun `startSync starts location updates`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        verify { locationRepository.startLocationUpdates() }
    }

    @Test
    fun `location updates are synced to camera`() = testScope.runTest {
        val locationFlow = MutableStateFlow<GpsLocation?>(null)
        every { locationRepository.locationUpdates } returns locationFlow
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        locationFlow.value = testLocation
        advanceUntilIdle()

        coVerify { cameraConnection.syncLocation(testLocation) }
    }

    @Test
    fun `stopSync transitions to Stopped state`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        syncCoordinator.stopSync()
        advanceUntilIdle()

        assertEquals(SyncState.Stopped, syncCoordinator.state.value)
    }

    @Test
    fun `stopSync disconnects from camera`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        syncCoordinator.stopSync()
        advanceUntilIdle()

        coVerify { cameraConnection.disconnect() }
    }

    @Test
    fun `stopSync stops location updates`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        syncCoordinator.stopSync()
        advanceUntilIdle()

        verify { locationRepository.stopLocationUpdates() }
    }

    @Test
    fun `stopSync clears syncing flag`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()
        assertTrue(syncCoordinator.isSyncing())

        syncCoordinator.stopSync()
        advanceUntilIdle()

        assertFalse(syncCoordinator.isSyncing())
    }

    @Test
    fun `connection error transitions to Disconnected state`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } throws RuntimeException("Connection failed")

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        val state = syncCoordinator.state.value
        assertTrue(state is SyncState.Disconnected)
        assertEquals(testCamera, (state as SyncState.Disconnected).camera)
    }

    @Test
    fun `duplicate startSync calls are ignored`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        syncCoordinator.startSync(testCamera) // Should be ignored

        advanceUntilIdle()

        // Should only connect once
        coVerify(exactly = 1) { cameraRepository.connect(testCamera) }
    }

    @Test
    fun `findAndSync locates camera and starts sync`() = testScope.runTest {
        every { cameraRepository.findCameraByMacAddress(testCamera.macAddress) } returns flowOf(testCamera)
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.findAndSync(testCamera.macAddress)
        advanceUntilIdle()

        verify { cameraRepository.findCameraByMacAddress(testCamera.macAddress) }
        coVerify { cameraRepository.connect(testCamera) }
    }

    @Test
    fun `syncing state includes firmware version`() = testScope.runTest {
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "2.5.1"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        val state = syncCoordinator.state.value as SyncState.Syncing
        assertEquals("2.5.1", state.firmwareVersion)
    }

    @Test
    fun `syncing state updates with location sync info`() = testScope.runTest {
        val locationFlow = MutableStateFlow<GpsLocation?>(null)
        every { locationRepository.locationUpdates } returns locationFlow
        coEvery { cameraRepository.connect(testCamera) } returns cameraConnection
        coEvery { cameraConnection.readFirmwareVersion() } returns "1.0.0"

        syncCoordinator.startSync(testCamera)
        advanceUntilIdle()

        locationFlow.value = testLocation
        advanceUntilIdle()

        val state = syncCoordinator.state.value as SyncState.Syncing
        assertEquals(testLocation, state.lastSyncInfo?.location)
    }
}
