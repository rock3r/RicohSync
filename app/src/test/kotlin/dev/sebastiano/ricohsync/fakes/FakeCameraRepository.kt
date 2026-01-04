package dev.sebastiano.ricohsync.fakes

import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.repository.CameraConnection
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class FakeCameraRepository : CameraRepository {

    private val _discoveredCameras = MutableSharedFlow<Camera>()
    override val discoveredCameras: Flow<Camera> = _discoveredCameras

    var startScanningCalled = false
        private set

    var stopScanningCalled = false
        private set

    var connectDelay = 0L
    var connectException: Exception? = null
    var connectionToReturn: FakeCameraConnection? = null
    /**
     * When true, throws exception if connectionToReturn is null (default: false for backward
     * compatibility)
     */
    var failIfConnectionNull = false
    var cameraToReturn: Camera? = null
    var connectCallCount = 0
        private set

    override fun startScanning() {
        startScanningCalled = true
    }

    override fun stopScanning() {
        stopScanningCalled = true
    }

    override fun findCameraByMacAddress(macAddress: String): Flow<Camera> = flow {
        cameraToReturn?.let { emit(it) }
    }

    override suspend fun connect(camera: Camera, onFound: (() -> Unit)?): CameraConnection {
        connectCallCount++
        if (connectDelay > 0) delay(connectDelay)
        onFound?.invoke()
        connectException?.let { throw it }
        if (failIfConnectionNull && connectionToReturn == null) {
            throw RuntimeException("Connection not available (connectionToReturn is null)")
        }
        return connectionToReturn ?: FakeCameraConnection(camera)
    }

    suspend fun emitDiscoveredCamera(camera: Camera) {
        _discoveredCameras.emit(camera)
    }
}
