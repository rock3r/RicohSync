package dev.sebastiano.ricohsync.data.repository

import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.PairedDevice
import dev.sebastiano.ricohsync.fakes.FakeCameraVendor
import dev.sebastiano.ricohsync.fakes.FakePairedDevicesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakePairedDevicesRepositoryTest {

    private lateinit var repository: FakePairedDevicesRepository

    private val testCamera = Camera(
        identifier = "00:11:22:33:44:55",
        name = "Test Camera",
        macAddress = "00:11:22:33:44:55",
        vendor = FakeCameraVendor,
    )

    private val testDevice = PairedDevice(
        macAddress = "00:11:22:33:44:55",
        name = "Test Camera",
        vendorId = "fake",
        isEnabled = true,
    )

    @Before
    fun setUp() {
        repository = FakePairedDevicesRepository()
    }

    @Test
    fun `initial state is empty`() = runTest {
        val devices = repository.pairedDevices.first()
        assertTrue(devices.isEmpty())
        assertFalse(repository.hasAnyDevices())
        assertFalse(repository.hasEnabledDevices())
    }

    @Test
    fun `addDevice adds a new device`() = runTest {
        repository.addDevice(testCamera, enabled = true)

        val devices = repository.pairedDevices.first()
        assertEquals(1, devices.size)
        assertEquals(testCamera.macAddress, devices[0].macAddress)
        assertEquals(testCamera.name, devices[0].name)
        assertTrue(devices[0].isEnabled)
        assertTrue(repository.addDeviceCalled)
        assertEquals(testCamera, repository.lastAddedCamera)
    }

    @Test
    fun `addDevice with enabled false creates disabled device`() = runTest {
        repository.addDevice(testCamera, enabled = false)

        val devices = repository.pairedDevices.first()
        assertFalse(devices[0].isEnabled)
    }

    @Test
    fun `removeDevice removes device`() = runTest {
        repository.addTestDevice(testDevice)
        assertTrue(repository.hasAnyDevices())

        repository.removeDevice(testDevice.macAddress)

        assertFalse(repository.hasAnyDevices())
        assertTrue(repository.removeDeviceCalled)
        assertEquals(testDevice.macAddress, repository.lastRemovedMacAddress)
    }

    @Test
    fun `setDeviceEnabled changes enabled state`() = runTest {
        repository.addTestDevice(testDevice.copy(isEnabled = false))

        repository.setDeviceEnabled(testDevice.macAddress, true)

        val device = repository.getDevice(testDevice.macAddress)
        assertTrue(device!!.isEnabled)
        assertTrue(repository.setDeviceEnabledCalled)
    }

    @Test
    fun `enabledDevices only returns enabled devices`() = runTest {
        repository.addTestDevice(testDevice.copy(isEnabled = true))
        repository.addTestDevice(
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Disabled Device",
                vendorId = "fake",
                isEnabled = false,
            ),
        )

        val enabledDevices = repository.enabledDevices.first()
        assertEquals(1, enabledDevices.size)
        assertEquals(testDevice.macAddress, enabledDevices[0].macAddress)
    }

    @Test
    fun `isDevicePaired returns true for paired device`() = runTest {
        repository.addTestDevice(testDevice)

        assertTrue(repository.isDevicePaired(testDevice.macAddress))
        assertFalse(repository.isDevicePaired("unknown-mac"))
    }

    @Test
    fun `getDevice returns device or null`() = runTest {
        repository.addTestDevice(testDevice)

        val device = repository.getDevice(testDevice.macAddress)
        assertEquals(testDevice, device)

        val notFound = repository.getDevice("unknown-mac")
        assertNull(notFound)
    }

    @Test
    fun `hasEnabledDevices returns correct value`() = runTest {
        assertFalse(repository.hasEnabledDevices())

        repository.addTestDevice(testDevice.copy(isEnabled = false))
        assertFalse(repository.hasEnabledDevices())

        repository.setDeviceEnabled(testDevice.macAddress, true)
        assertTrue(repository.hasEnabledDevices())
    }

    @Test
    fun `updateDeviceName updates name`() = runTest {
        repository.addTestDevice(testDevice)

        repository.updateDeviceName(testDevice.macAddress, "New Name")

        val device = repository.getDevice(testDevice.macAddress)
        assertEquals("New Name", device?.name)
    }

    @Test
    fun `updateDeviceName with null clears name`() = runTest {
        repository.addTestDevice(testDevice)

        repository.updateDeviceName(testDevice.macAddress, null)

        val device = repository.getDevice(testDevice.macAddress)
        assertNull(device?.name)
    }

    @Test
    fun `clear removes all devices`() = runTest {
        repository.addTestDevice(testDevice)
        repository.addTestDevice(
            PairedDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "Another Device",
                vendorId = "fake",
                isEnabled = true,
            ),
        )

        repository.clear()

        assertFalse(repository.hasAnyDevices())
    }

    @Test
    fun `resetTracking clears tracking flags`() = runTest {
        repository.addDevice(testCamera, enabled = true)
        assertTrue(repository.addDeviceCalled)

        repository.resetTracking()

        assertFalse(repository.addDeviceCalled)
        assertNull(repository.lastAddedCamera)
    }
}

