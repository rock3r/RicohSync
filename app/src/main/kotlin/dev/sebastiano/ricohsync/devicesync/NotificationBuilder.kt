package dev.sebastiano.ricohsync.devicesync

import android.app.Notification
import android.app.PendingIntent

/**
 * Interface for building notifications, allowing testability by providing a fake implementation.
 */
interface NotificationBuilder {
    fun build(
        channelId: String,
        title: String,
        content: String,
        icon: Int,
        isOngoing: Boolean = false,
        priority: Int = android.app.Notification.PRIORITY_DEFAULT,
        category: String? = null,
        isSilent: Boolean = false,
        actions: List<NotificationAction> = emptyList(),
    ): Notification
}

data class NotificationAction(val icon: Int, val title: String, val pendingIntent: PendingIntent)
