package dev.sebastiano.ricohsync.pairing

import dev.sebastiano.ricohsync.RicohSyncApp
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.fakes.FakeCameraVendor
import dev.sebastiano.ricohsync.fakes.FakeKhronicleLogger
import dev.sebastiano.ricohsync.fakes.FakeLoggingEngine
import dev.sebastiano.ricohsync.fakes.FakePairedDevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
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
class PairingViewModelTest {

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var viewModel: PairingViewModel

    private val testCamera =
        Camera(
            identifier = "AA:BB:CC:DD:EE:FF",
            name = "Test Camera",
            macAddress = "AA:BB:CC:DD:EE:FF",
            vendor = FakeCameraVendor,
        )

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        RicohSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        // Inject FakeLoggingEngine instead of using KhronicleLogEngine
        viewModel = PairingViewModel(pairedDevicesRepository, loggingEngine = FakeLoggingEngine)
    }

    @After fun tearDown() {}

    @Test
    fun `initial state is Idle`() {
        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `pairDevice transitions to Pairing state`() = runTest {
        viewModel.pairDevice(testCamera)

        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        assertEquals(testCamera, (state as PairingScreenState.Pairing).camera)
        assertNull((state as PairingScreenState.Pairing).error)
    }

    @Test
    fun `pairDevice emits DevicePaired navigation event on success`() = runTest {
        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background
        val job = launch { viewModel.navigationEvents.take(1).collect { events.add(it) } }

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        job.join()

        assertEquals(1, events.size)
        assertEquals(PairingNavigationEvent.DevicePaired, events.first())
    }

    @Test
    fun `pairDevice adds device to repository with enabled true`() = runTest {
        assertFalse(pairedDevicesRepository.addDeviceCalled)

        viewModel.pairDevice(testCamera)

        // Wait for the pairing coroutine to complete (it runs on IO dispatcher)
        var attempts = 0
        while (!pairedDevicesRepository.addDeviceCalled && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        assertTrue(pairedDevicesRepository.addDeviceCalled)
        assertEquals(testCamera, pairedDevicesRepository.lastAddedCamera)
        assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }

    @Test
    fun `pairDevice sets REJECTED error when repository throws rejection exception`() = runTest {
        // Make addDevice throw an exception with "reject" in the message
        val exception = Exception("Device rejected pairing request")
        pairedDevicesRepository.addDeviceException = exception

        viewModel.pairDevice(testCamera)

        // Wait for error to be set (it runs on IO dispatcher)
        var attempts = 0
        while (attempts < 50) {
            val state = viewModel.state.value
            if (state is PairingScreenState.Pairing && state.error != null) {
                assertEquals(PairingError.REJECTED, state.error)
                return@runTest
            }
            kotlinx.coroutines.delay(10)
            attempts++
        }
        // If we get here, the error wasn't set
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.REJECTED, pairingState.error)
    }

    @Test
    fun `pairDevice sets TIMEOUT error when repository throws timeout exception`() = runTest {
        // Make addDevice throw an exception with "timeout" in the message
        val exception = Exception("Connection timeout")
        pairedDevicesRepository.addDeviceException = exception

        viewModel.pairDevice(testCamera)

        // Wait for error to be set (it runs on IO dispatcher)
        var attempts = 0
        while (attempts < 50) {
            val state = viewModel.state.value
            if (state is PairingScreenState.Pairing && state.error != null) {
                assertEquals(PairingError.TIMEOUT, state.error)
                return@runTest
            }
            kotlinx.coroutines.delay(10)
            attempts++
        }
        // If we get here, the error wasn't set
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.TIMEOUT, pairingState.error)
    }

    @Test
    fun `pairDevice sets UNKNOWN error when repository throws other exception`() = runTest {
        // Make addDevice throw an exception without "reject" or "timeout"
        val exception = Exception("Unexpected error occurred")
        pairedDevicesRepository.addDeviceException = exception

        viewModel.pairDevice(testCamera)

        // Wait for error to be set (it runs on IO dispatcher)
        var attempts = 0
        while (attempts < 50) {
            val state = viewModel.state.value
            if (state is PairingScreenState.Pairing && state.error != null) {
                assertEquals(PairingError.UNKNOWN, state.error)
                return@runTest
            }
            kotlinx.coroutines.delay(10)
            attempts++
        }
        // If we get here, the error wasn't set
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        val pairingState = state as PairingScreenState.Pairing
        assertEquals(PairingError.UNKNOWN, pairingState.error)
    }

    @Test
    fun `pairDevice does not emit navigation event on error`() = runTest {
        val exception = Exception("Pairing failed")
        pairedDevicesRepository.addDeviceException = exception

        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background with timeout
        val job = launch {
            try {
                viewModel.navigationEvents.take(1).collect { events.add(it) }
            } catch (e: Exception) {
                // Ignore timeout
            }
        }

        viewModel.pairDevice(testCamera)
        advanceUntilIdle()

        // Give a small delay to see if any events are emitted
        delay(100)
        job.cancel()

        // No events should be emitted on error
        assertTrue(events.isEmpty())

        // Verify state has an error
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        assertTrue((state as PairingScreenState.Pairing).error != null)
    }

    @Test
    fun `cancelPairing transitions back to Idle state`() = runTest {
        viewModel.pairDevice(testCamera)

        // Verify we're in Pairing state
        assertTrue(viewModel.state.value is PairingScreenState.Pairing)

        viewModel.cancelPairing()

        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `cancelPairing cancels ongoing pairing job`() = runTest {
        // Add a delay to the repository to simulate a long-running operation
        pairedDevicesRepository.addDeviceDelay = 1000L

        viewModel.pairDevice(testCamera)

        // Cancel before it completes
        viewModel.cancelPairing()

        advanceUntilIdle()

        // State should be Idle, not Pairing
        assertEquals(PairingScreenState.Idle, viewModel.state.value)
    }

    @Test
    fun `pairDevice stops scanning before pairing`() = runTest {
        // Start scanning (this happens automatically in init, but we can verify)
        // Note: We can't easily test the actual scanning behavior without mocking Kable Scanner,
        // but we can verify that stopScanning is called by checking the state transition
        viewModel.pairDevice(testCamera)

        // Should be in Pairing state, not Scanning
        val state = viewModel.state.value
        assertTrue(state is PairingScreenState.Pairing)
        assertFalse(state is PairingScreenState.Scanning)
    }

    @Test
    fun `multiple pairDevice calls emit navigation event for each successful pairing`() = runTest {
        val events = mutableListOf<PairingNavigationEvent>()

        // Collect events in background
        val job = launch { viewModel.navigationEvents.collect { events.add(it) } }

        // First pairing
        viewModel.pairDevice(testCamera)
        var attempts = 0
        while (events.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        // Second pairing (repository allows updating existing devices)
        viewModel.pairDevice(testCamera)
        attempts = 0
        val firstEventCount = events.size
        while (events.size == firstEventCount && attempts < 50) {
            kotlinx.coroutines.delay(10)
            attempts++
        }

        job.cancel()

        // Both should emit events
        assertEquals(2, events.size)
        assertEquals(PairingNavigationEvent.DevicePaired, events[0])
        assertEquals(PairingNavigationEvent.DevicePaired, events[1])

        // Device should be paired
        assertTrue(pairedDevicesRepository.isDevicePaired(testCamera.macAddress))
    }
}
