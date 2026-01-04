package dev.sebastiano.camerasync.devicesync

import dev.sebastiano.camerasync.domain.model.GpsLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for centralized location collection.
 *
 * This abstracts the location collection mechanism from the sync logic, allowing multiple device
 * connections to share a single location stream.
 */
interface LocationCollector {

    /**
     * Flow of location updates. Emits the latest location whenever it changes. May emit null if no
     * location is available yet.
     */
    val locationUpdates: StateFlow<GpsLocation?>

    /** Whether location collection is currently active. */
    val isCollecting: StateFlow<Boolean>

    /**
     * Starts collecting location updates.
     *
     * Multiple calls are idempotent - calling start when already collecting will have no effect.
     */
    fun startCollecting()

    /**
     * Stops collecting location updates.
     *
     * Should be called when no devices need location updates.
     */
    fun stopCollecting()
}

/**
 * Manages location collection lifecycle based on active device count.
 *
 * This coordinator automatically starts/stops location collection based on whether there are any
 * devices that need location updates.
 */
interface LocationCollectionCoordinator : LocationCollector {

    /**
     * Registers a device as needing location updates. Location collection will start if not already
     * running.
     *
     * @param deviceId Unique identifier for the device (typically MAC address).
     */
    fun registerDevice(deviceId: String)

    /**
     * Unregisters a device from needing location updates. If no more devices need updates, location
     * collection will stop.
     *
     * @param deviceId Unique identifier for the device.
     */
    fun unregisterDevice(deviceId: String)

    /** Returns the number of devices currently registered for updates. */
    fun getRegisteredDeviceCount(): Int
}
