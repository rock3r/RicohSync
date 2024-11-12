package dev.sebastiano.ricohsync.devicesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import dev.sebastiano.ricohsync.R

internal const val NOTIFICATION_CHANNEL = "SYNC_SERVICE_NOTIFICATION_CHANNEL"

fun createForegroundServiceNotification(context: Context): Notification =
    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
        .setOngoing(true)
        .setPriority(PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSilent(true)
        .setContentTitle("Connected to camera")
        .setContentText("Syncing GPS and time to camera")
        .setSmallIcon(R.drawable.ic_sync)
        .build()

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
