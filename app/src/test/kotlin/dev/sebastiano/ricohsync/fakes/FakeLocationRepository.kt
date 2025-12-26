package dev.sebastiano.ricohsync.fakes

import dev.sebastiano.ricohsync.domain.model.GpsLocation
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLocationRepository : LocationRepository {

    override val locationUpdates = MutableStateFlow<GpsLocation?>(null)

    var startLocationUpdatesCalled = false
        private set

    var stopLocationUpdatesCalled = false
        private set

    override fun startLocationUpdates() {
        startLocationUpdatesCalled = true
    }

    override fun stopLocationUpdates() {
        stopLocationUpdatesCalled = true
    }

    fun emit(location: GpsLocation?) {
        locationUpdates.value = location
    }
}
