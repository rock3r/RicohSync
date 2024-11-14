package dev.sebastiano.ricohsync.devicesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import dev.sebastiano.ricohsync.R
import java.text.DateFormat
import java.time.ZonedDateTime

internal const val NOTIFICATION_CHANNEL = "SYNC_SERVICE_NOTIFICATION_CHANNEL"

internal fun createForegroundServiceNotification(
    context: Context,
    state: DeviceSyncService.State
): Notification =
    when (state) {
        is DeviceSyncService.State.Connecting -> createConnectingNotification(
            context,
            state.advertisement
        )

        DeviceSyncService.State.Idle -> createIdleNotification(context)
        is DeviceSyncService.State.Syncing -> createSyncingNotification(
            context,
            state.peripheral,
            state.lastSyncTime
        )
    }

private fun createConnectingNotification(context: Context, advertisement: Advertisement) =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSilent(true)
        .setContentTitle("Connecting to ${advertisement.name}...")
        .setContentText("Syncing will begin shortly")
        .setSmallIcon(R.drawable.ic_sync_disabled)
        .build()


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

private fun createSyncingNotification(
    context: Context,
    peripheral: Peripheral,
    lastSyncTime: ZonedDateTime?
): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_LOCATION_SHARING)
        .setSilent(true)
        .setContentTitle("Syncing with ${peripheral.name}")
        .setContentText("Last update: ${formatElapsedTimeSince(lastSyncTime)}")
        .setSmallIcon(R.drawable.ic_sync)
        .build()

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

fun createErrorNotificationBuilder(context: Context): NotificationCompat.Builder =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_sync_error)
        .setPriority(PRIORITY_HIGH)
        .setCategory(Notification.CATEGORY_ERROR)

fun registerNotificationChannel(context: Context) {
    val channel =
        NotificationChannel(
            /* id = */ NOTIFICATION_CHANNEL,
            /* name = */ "Camera connection",
            /* importance = */ NotificationManager.IMPORTANCE_MIN,
        )

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.createNotificationChannel(channel)
}
