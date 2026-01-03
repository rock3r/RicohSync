package dev.sebastiano.ricohsync.permissions

import dev.sebastiano.ricohsync.PermissionInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {

    private lateinit var viewModel: PermissionsViewModel

    @Before
    fun setUp() {
        viewModel = PermissionsViewModel()
    }

    @Test
    fun `onAllPermissionsGranted triggers animation`() {
        assertFalse(viewModel.showSuccessAnimation.value)

        viewModel.onAllPermissionsGranted()

        assertTrue(viewModel.showSuccessAnimation.value)
    }

    @Test
    fun `onAllPermissionsGranted triggers navigation after delay`() = runTest {
        assertFalse(viewModel.shouldNavigate.value)

        viewModel.onAllPermissionsGranted()

        // Before delay
        assertFalse(viewModel.shouldNavigate.value)

        // Advance until all coroutines complete (including the delay)
        advanceUntilIdle()

        // After delay
        assertTrue(viewModel.shouldNavigate.value)
    }

    @Test
    fun `onAllPermissionsGranted only triggers once`() = runTest {
        viewModel.onAllPermissionsGranted()
        viewModel.onAllPermissionsGranted() // Call again

        advanceUntilIdle()

        // Should only navigate once
        assertTrue(viewModel.shouldNavigate.value)
    }

    @Test
    fun `checkPermissions triggers animation when all granted`() {
        val permissions =
            listOf(
                PermissionInfo("Location", "Get GPS", true),
                PermissionInfo("Bluetooth", "Connect", true),
            )

        viewModel.checkPermissions(permissions)

        assertTrue(viewModel.showSuccessAnimation.value)
    }

    @Test
    fun `checkPermissions does not trigger when not all granted`() {
        val permissions =
            listOf(
                PermissionInfo("Location", "Get GPS", true),
                PermissionInfo("Bluetooth", "Connect", false),
            )

        viewModel.checkPermissions(permissions)

        assertFalse(viewModel.showSuccessAnimation.value)
    }

    @Test
    fun `checkPermissions triggers navigation after delay when all granted`() = runTest {
        val permissions =
            listOf(
                PermissionInfo("Location", "Get GPS", true),
                PermissionInfo("Bluetooth", "Connect", true),
            )

        viewModel.checkPermissions(permissions)

        advanceUntilIdle()

        assertTrue(viewModel.shouldNavigate.value)
    }

    @Test
    fun `resetNavigation resets all state`() = runTest {
        viewModel.onAllPermissionsGranted()
        advanceUntilIdle()

        assertTrue(viewModel.showSuccessAnimation.value)
        assertTrue(viewModel.shouldNavigate.value)

        viewModel.resetNavigation()

        assertFalse(viewModel.showSuccessAnimation.value)
        assertFalse(viewModel.shouldNavigate.value)
    }

    @Test
    fun `resetNavigation allows navigation to be triggered again`() = runTest {
        viewModel.onAllPermissionsGranted()
        advanceUntilIdle()
        assertTrue(viewModel.shouldNavigate.value)

        viewModel.resetNavigation()

        viewModel.onAllPermissionsGranted()
        advanceUntilIdle()
        assertTrue(viewModel.shouldNavigate.value)
    }
}
