package dev.sebastiano.ricohsync.devicesync

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.sebastiano.ricohsync.data.repository.FusedLocationRepository
import dev.sebastiano.ricohsync.data.repository.KableCameraRepository
import dev.sebastiano.ricohsync.domain.model.RicohCamera
import dev.sebastiano.ricohsync.domain.model.SyncState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

private const val TAG = "DeviceSyncService"

/**
 * Foreground service that manages the sync process with a Ricoh camera.
 *
 * This service handles:
 * - Running as a foreground service for background operation
 * - Managing notifications
 * - Permission checking
 *
 * The actual sync logic is delegated to [SyncCoordinator].
 */
internal class DeviceSyncService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + CoroutineName("DeviceSyncService") + SupervisorJob()

    private val binder by lazy { DeviceSyncServiceBinder() }

    private val vibrator by lazy { SyncErrorVibrator(applicationContext) }

    private val syncCoordinator by lazy {
        SyncCoordinator(
            cameraRepository =
                KableCameraRepository(
                    vendorRegistry = dev.sebastiano.ricohsync.RicohSyncApp.createVendorRegistry()
                ),
            locationRepository = FusedLocationRepository(applicationContext),
            coroutineScope = this,
        )
    }

    /** Exposes the sync state as the UI-facing DeviceSyncState. */
    val state: Flow<DeviceSyncState> =
        syncCoordinator.state
            .map { it.toDeviceSyncState() }
            .shareIn(this, SharingStarted.WhileSubscribed())

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        Log.d(TAG, "App brought to foreground, stopping vibration")
                        vibrator.stop()
                    }
                }
            )
    }

    override fun onBind(intent: Intent): IBinder {
        if (!checkPermissions()) {
            throw RuntimeException("Required permissions not granted")
        }
        return binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            STOP_INTENT_ACTION -> {
                Log.i(TAG, "Received stop intent, disconnecting...")
                launch { stopAndDisconnect() }
            }
            else -> {
                if (!checkPermissions()) return START_NOT_STICKY
                startForeground()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Connects to the camera and starts syncing.
     *
     * @param camera The camera to connect to.
     */
    fun connectAndSync(camera: RicohCamera) {
        syncCoordinator.startSync(camera)

        // Update notification when state changes
        launch {
            syncCoordinator.state.collect { state ->
                updateNotification(state)
                if (state is SyncState.Disconnected) {
                    vibrator.vibrate()
                }
            }
        }
    }

    /** Stops syncing and disconnects from the camera. */
    suspend fun stopAndDisconnect() {
        syncCoordinator.stopSync()
        stopSelf()
    }

    private fun startForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createForegroundServiceNotification(this, syncCoordinator.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            if (e is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Cannot start foreground service", e)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions =
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        for (permission in requiredPermissions) {
            val result = PermissionChecker.checkSelfPermission(this, permission)
            if (result != PermissionChecker.PERMISSION_GRANTED) {
                stopAndNotifyMissingPermission(permission)
                return false
            }
        }
        return true
    }

    private fun stopAndNotifyMissingPermission(missingPermission: String) {
        stopSelf()

        val notification =
            createErrorNotificationBuilder(this)
                .setContentTitle("Missing permission")
                .setContentText("Cannot sync with camera: $missingPermission is required")
                .build()

        val hasNotificationPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasNotificationPermission) {
            Log.e(TAG, "Cannot show missing permission notification: $missingPermission")
            return
        }

        vibrator.vibrate()
        NotificationManagerCompat.from(this).notify(ERROR_NOTIFICATION_ID, notification)
    }

    private fun updateNotification(state: SyncState) {
        val notification = createForegroundServiceNotification(this, state)
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Location updates are now managed by SyncCoordinator
        return super.onUnbind(intent)
    }

    inner class DeviceSyncServiceBinder : Binder() {
        fun getService(): DeviceSyncService = this@DeviceSyncService
    }

    companion object {
        private const val NOTIFICATION_ID = 111
        private const val ERROR_NOTIFICATION_ID = 123
        const val STOP_REQUEST_CODE = 666
        const val STOP_INTENT_ACTION = "disconnect_camera"

        fun getInstanceFrom(binder: Binder): DeviceSyncService =
            (binder as DeviceSyncServiceBinder).getService()

        fun createDisconnectIntent(context: Context): Intent =
            Intent(context, DeviceSyncService::class.java).apply {
                action = STOP_INTENT_ACTION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}

/** Maps domain SyncState to the UI-facing DeviceSyncState. */
private fun SyncState.toDeviceSyncState(): DeviceSyncState =
    when (this) {
        SyncState.Idle -> DeviceSyncState.Starting
        is SyncState.Connecting -> DeviceSyncState.Connecting(camera)
        is SyncState.Syncing ->
            DeviceSyncState.Syncing(
                camera = camera,
                firmwareVersion = firmwareVersion,
                syncInfo = lastSyncInfo,
            )
        is SyncState.Disconnected -> DeviceSyncState.Disconnected(camera)
        SyncState.Stopped -> DeviceSyncState.Stopped
    }
