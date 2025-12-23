package dev.sebastiano.ricohsync.domain.repository

import dev.sebastiano.ricohsync.domain.model.GpsLocation
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for location services.
 *
 * Abstracts the GPS/location provider to allow for easier testing.
 */
interface LocationRepository {

    /** Flow of location updates. Emits null if no location is available yet. */
    val locationUpdates: Flow<GpsLocation?>

    /** Start requesting location updates. */
    fun startLocationUpdates()

    /** Stop requesting location updates. */
    fun stopLocationUpdates()
}
