package dev.sebastiano.ricohsync.devices

import android.content.Context
import dev.sebastiano.ricohsync.RicohSyncApp
import dev.sebastiano.ricohsync.domain.model.PairedDevice
import dev.sebastiano.ricohsync.fakes.FakeKhronicleLogger
import dev.sebastiano.ricohsync.fakes.FakeLocationRepository
import dev.sebastiano.ricohsync.fakes.FakePairedDevicesRepository
import dev.sebastiano.ricohsync.fakes.FakeVendorRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesListViewModelTest {

    private lateinit var pairedDevicesRepository: FakePairedDevicesRepository
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var vendorRegistry: FakeVendorRegistry
    private lateinit var viewModel: DevicesListViewModel

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        RicohSyncApp.initializeLogging(FakeKhronicleLogger)

        pairedDevicesRepository = FakePairedDevicesRepository()
        locationRepository = FakeLocationRepository()
        vendorRegistry = FakeVendorRegistry()

        viewModel =
            DevicesListViewModel(
                pairedDevicesRepository = pairedDevicesRepository,
                locationRepository = locationRepository,
                bindingContextProvider = { mockContext() },
                vendorRegistry = vendorRegistry,
            )
    }

    @After
    fun tearDown() {
        // Clean up if needed
    }

    @Test
    fun `computeDeviceDisplayInfo returns correct make and model for single device`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "GR IIIx",
                vendorId = "fake",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DevicesListState.HasDevices)
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(displayInfo)
        assertEquals("Fake Camera", displayInfo!!.make)
        assertEquals("GR IIIx", displayInfo.model)
        assertFalse(displayInfo.showPairingName)
    }

    @Test
    fun `computeDeviceDisplayInfo shows pairing name when multiple devices have same make and model`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]

            assertNotNull(displayInfo1)
            assertNotNull(displayInfo2)

            // Both should show pairing name since they have the same make/model
            assertTrue(displayInfo1!!.showPairingName)
            assertTrue(displayInfo2!!.showPairingName)
            assertEquals("GR IIIx", displayInfo1.model)
            assertEquals("GR IIIx", displayInfo2.model)
        }

    @Test
    fun `computeDeviceDisplayInfo does not show pairing name when devices have different models`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "GR III",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]

            assertNotNull(displayInfo1)
            assertNotNull(displayInfo2)

            // Should not show pairing name since models are different
            assertFalse(displayInfo1!!.showPairingName)
            assertFalse(displayInfo2!!.showPairingName)
            assertEquals("GR IIIx", displayInfo1.model)
            assertEquals("GR III", displayInfo2.model)
        }

    @Test
    fun `computeDeviceDisplayInfo handles unknown vendor gracefully`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Unknown Camera",
                vendorId = "unknown_vendor",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DevicesListState.HasDevices)
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(displayInfo)
        // Should capitalize the vendorId when vendor is not found
        assertEquals("Unknown_vendor", displayInfo!!.make)
        assertEquals("Unknown Camera", displayInfo.model)
    }

    @Test
    fun `computeDeviceDisplayInfo handles null device name`() = runTest {
        val device =
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = null,
                vendorId = "fake",
                isEnabled = true,
            )

        pairedDevicesRepository.addTestDevice(device)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DevicesListState.HasDevices)
        val hasDevicesState = state as DevicesListState.HasDevices

        val displayInfo = hasDevicesState.displayInfoMap[device.macAddress]
        assertNotNull(displayInfo)
        assertEquals("Fake Camera", displayInfo!!.make)
        assertEquals("Unknown", displayInfo.model)
        assertNull(displayInfo.pairingName)
    }

    @Test
    fun `computeDeviceDisplayInfo shows pairing name only in collapsed state when duplicates exist`() =
        runTest {
            val device1 =
                PairedDevice(
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    name = "My Camera 1",
                    vendorId = "fake",
                    isEnabled = true,
                )
            val device2 =
                PairedDevice(
                    macAddress = "11:22:33:44:55:66",
                    name = "My Camera 2",
                    vendorId = "fake",
                    isEnabled = true,
                )
            // Both have same model name but different pairing names
            // This simulates the case where user renamed cameras but they're the same model
            val device3 =
                PairedDevice(
                    macAddress = "77:88:99:AA:BB:CC",
                    name = "GR IIIx",
                    vendorId = "fake",
                    isEnabled = true,
                )

            pairedDevicesRepository.addTestDevice(device1)
            pairedDevicesRepository.addTestDevice(device2)
            pairedDevicesRepository.addTestDevice(device3)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is DevicesListState.HasDevices)
            val hasDevicesState = state as DevicesListState.HasDevices

            val displayInfo1 = hasDevicesState.displayInfoMap[device1.macAddress]
            val displayInfo2 = hasDevicesState.displayInfoMap[device2.macAddress]
            val displayInfo3 = hasDevicesState.displayInfoMap[device3.macAddress]

            // device1 and device2 have same model (from pairing name), so should show pairing name
            // device3 has different model, so should not show pairing name
            assertTrue(displayInfo1!!.showPairingName)
            assertTrue(displayInfo2!!.showPairingName)
            assertFalse(displayInfo3!!.showPairingName)
        }

    private fun mockContext(): Context {
        // Return a minimal mock context that won't crash
        // The service binding will fail, but that's fine for these tests
        // since we're only testing display info computation
        return object : Context() {
            override fun getApplicationContext(): Context = this

            override fun getPackageName(): String = "test.package"

            override fun getSystemService(name: String): Any? = null

            override fun getResources() = throw UnsupportedOperationException()

            override fun getAssets() = throw UnsupportedOperationException()

            override fun getTheme() = throw UnsupportedOperationException()

            override fun getClassLoader() = throw UnsupportedOperationException()

            override fun getPackageManager() = throw UnsupportedOperationException()

            override fun getContentResolver() = throw UnsupportedOperationException()

            override fun getMainLooper() = throw UnsupportedOperationException()

            override fun getMainExecutor() = throw UnsupportedOperationException()

            override fun bindService(
                service: android.content.Intent?,
                conn: android.content.ServiceConnection?,
                flags: Int,
            ): Boolean = false // Return false to indicate binding failed (expected in tests)

            override fun unbindService(conn: android.content.ServiceConnection?) {
                // No-op
            }

            override fun startService(
                service: android.content.Intent?
            ): android.content.ComponentName? = null
        }
    }
}
