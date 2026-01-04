package dev.sebastiano.ricohsync.permissions

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.ricohsync.PermissionInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the permissions screen.
 *
 * Manages the permission state, animation triggers, and navigation timing.
 */
class PermissionsViewModel : ViewModel() {

    private val _showSuccessAnimation = mutableStateOf(false)
    val showSuccessAnimation: State<Boolean> = _showSuccessAnimation

    private val _shouldNavigate = MutableStateFlow(false)
    val shouldNavigate: StateFlow<Boolean> = _shouldNavigate.asStateFlow()

    private var navigationTriggered = false

    /**
     * Called when all permissions are granted. Triggers the success animation and schedules
     * navigation after a delay.
     */
    fun onAllPermissionsGranted() {
        if (!_showSuccessAnimation.value) {
            _showSuccessAnimation.value = true
            triggerNavigationAfterDelay()
        }
    }

    /** Checks if all permissions in the list are granted and triggers the animation if so. */
    fun checkPermissions(permissions: List<PermissionInfo>) {
        val allGranted = permissions.all { it.isGranted }
        if (allGranted) {
            if (!_showSuccessAnimation.value) {
                _showSuccessAnimation.value = true
            }
            triggerNavigationAfterDelay()
        }
    }

    private fun triggerNavigationAfterDelay() {
        if (navigationTriggered) return
        navigationTriggered = true

        viewModelScope.launch {
            delay(1000) // Wait 1 second after animation starts
            _shouldNavigate.value = true
        }
    }

    /** Resets the navigation state (useful for testing or if screen is shown again). */
    fun resetNavigation() {
        navigationTriggered = false
        _shouldNavigate.value = false
        _showSuccessAnimation.value = false
    }
}
