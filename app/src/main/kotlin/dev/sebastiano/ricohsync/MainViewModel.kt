package dev.sebastiano.ricohsync

import androidx.lifecycle.ViewModel
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository

/**
 * Main ViewModel that manages the app's navigation state.
 *
 * Handles permission state and navigation between:
 * - Permissions screen (when permissions not granted)
 * - Devices list screen (main screen with paired devices)
 * - Pairing screen (for adding new devices)
 */
class MainViewModel(private val pairedDevicesRepository: PairedDevicesRepository) : ViewModel() {
    // Navigation is now handled via Nav3 NavBackStack in the UI layer
}
