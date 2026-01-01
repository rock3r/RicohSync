package dev.sebastiano.ricohsync.devicesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import dev.sebastiano.ricohsync.R
import dev.sebastiano.ricohsync.domain.model.Camera
import dev.sebastiano.ricohsync.domain.model.SyncState
import java.text.DateFormat
import java.time.ZonedDateTime

internal const val NOTIFICATION_CHANNEL = "SYNC_SERVICE_NOTIFICATION_CHANNEL"

/**
 * Creates a notification for the foreground service based on the current sync state.
 */
internal fun createForegroundServiceNotification(
    context: Context,
    state: SyncState,
): Notification = when (state) {
    SyncState.Idle -> createIdleNotification(context)
    is SyncState.Connecting -> createConnectingNotification(context, state.camera)
    is SyncState.Syncing -> createSyncingNotification(
        context,
        state.camera,
        state.lastSyncInfo?.syncTime,
    )
    is SyncState.Disconnected -> createReconnectingNotification(context, state.camera)
    SyncState.Stopped -> error("No notification needed when stopped")
}

private fun createIdleNotification(context: Context): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSilent(true)
        .setContentTitle("Waiting for camera...")
        .setContentText("Will start syncing when camera is in range")
        .setSmallIcon(R.drawable.ic_sync_disabled)
        .build()

private fun createConnectingNotification(context: Context, camera: Camera): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSilent(true)
        .setContentTitle("Connecting to ${camera.name ?: "camera"}...")
        .setContentText("Syncing will begin shortly")
        .setSmallIcon(R.drawable.ic_sync_disabled)
        .build()

private fun createReconnectingNotification(context: Context, camera: Camera): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSilent(true)
        .setContentTitle("Reconnecting to ${camera.name ?: "camera"}...")
        .setContentText("Connection to the device lost")
        .setSmallIcon(R.drawable.ic_sync_error)
        .build()

private fun createSyncingNotification(
    context: Context,
    camera: Camera,
    lastSyncTime: ZonedDateTime?,
): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_LOCATION_SHARING)
        .setSilent(true)
        .setContentTitle("Syncing with ${camera.name ?: "camera"}")
        .setContentText("Last update: ${formatElapsedTimeSince(lastSyncTime)}")
        .setSmallIcon(R.drawable.ic_sync)
        .addAction(
            NotificationCompat.Action.Builder(
                /* icon = */ 0,
                /* title = */ "Disconnect",
                /* intent = */ PendingIntent.getService(
                    context,
                    DeviceSyncService.STOP_REQUEST_CODE,
                    DeviceSyncService.createDisconnectIntent(context),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        .build()

/**
 * Formats the elapsed time since the last sync in a human-readable format.
 */
internal fun formatElapsedTimeSince(lastSyncTime: ZonedDateTime?): String {
    if (lastSyncTime == null) return "never"
    val syncTime = lastSyncTime.toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    if (now - syncTime < 5 * DateUtils.SECOND_IN_MILLIS) return "just now"

    return DateUtils.formatSameDayTime(
        /* then = */ syncTime,
        /* now = */ now,
        /* dateStyle = */ DateFormat.SHORT,
        /* timeStyle = */ DateFormat.SHORT,
    ).toString()
}

/**
 * Creates a notification builder for error notifications.
 */
fun createErrorNotificationBuilder(context: Context): NotificationCompat.Builder =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_sync_error)
        .setPriority(PRIORITY_HIGH)
        .setCategory(Notification.CATEGORY_ERROR)

/**
 * Registers the notification channel for the sync service.
 *
 * Should be called during app initialization.
 */
fun registerNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        /* id = */ NOTIFICATION_CHANNEL,
        /* name = */ "Camera connection",
        /* importance = */ NotificationManager.IMPORTANCE_MIN,
    )

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.createNotificationChannel(channel)
}

// --- Multi-device notification functions ---

/**
 * Creates a notification for multi-device sync showing connection status.
 */
internal fun createMultiDeviceNotification(
    context: Context,
    connectedCount: Int,
    totalEnabled: Int,
    lastSyncTime: java.time.ZonedDateTime?,
): Notification {
    val title = when {
        connectedCount == 0 && totalEnabled == 0 -> "No devices enabled"
        connectedCount == 0 -> "Searching for $totalEnabled device${if (totalEnabled > 1) "s" else ""}..."
        connectedCount == 1 -> "Syncing with 1 device"
        else -> "Syncing with $connectedCount devices"
    }

    val content = when {
        connectedCount == 0 && totalEnabled == 0 -> "Enable devices to start syncing"
        connectedCount == 0 -> "Will connect when cameras are in range"
        lastSyncTime != null -> "Last sync: ${formatElapsedTimeSince(lastSyncTime)}"
        else -> "Connected and syncing"
    }

    val icon = when {
        connectedCount == 0 -> R.drawable.ic_sync_disabled
        else -> R.drawable.ic_sync
    }

    return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_LOCATION_SHARING)
        .setSilent(true)
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(icon)
        .addAction(
            NotificationCompat.Action.Builder(
                /* icon = */ 0,
                /* title = */ "Refresh",
                /* intent = */ PendingIntent.getService(
                    context,
                    MultiDeviceSyncService.REFRESH_REQUEST_CODE,
                    MultiDeviceSyncService.createRefreshIntent(context),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        .addAction(
            NotificationCompat.Action.Builder(
                /* icon = */ 0,
                /* title = */ "Stop all",
                /* intent = */ PendingIntent.getService(
                    context,
                    MultiDeviceSyncService.STOP_REQUEST_CODE,
                    MultiDeviceSyncService.createStopIntent(context),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        )
        .build()
}