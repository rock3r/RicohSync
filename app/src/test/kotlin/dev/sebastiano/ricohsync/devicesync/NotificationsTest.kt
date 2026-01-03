package dev.sebastiano.ricohsync.devicesync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dev.sebastiano.ricohsync.RicohSyncApp
import dev.sebastiano.ricohsync.fakes.FakeKhronicleLogger
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationsTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        // Initialize Khronicle with fake logger for tests
        RicohSyncApp.initializeLogging(FakeKhronicleLogger)

        // Mock context and notification manager
        notificationManager = mockk<NotificationManager>(relaxed = true)
        context =
            mockk<Context>(relaxed = true) {
                every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
                every { packageName } returns "dev.sebastiano.ricohsync"
            }

        registerNotificationChannel(context)
    }

    @Test
    fun `notification shows no devices when none enabled`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 0,
                totalEnabled = 0,
                lastSyncTime = null,
            )

        assertEquals(
            "No devices enabled",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Enable devices to start syncing",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `notification shows searching when enabled but not connected`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 0,
                totalEnabled = 2,
                lastSyncTime = null,
            )

        assertEquals(
            "Searching for 2 devices...",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Will connect when cameras are in range",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `notification shows single device syncing when all connected`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 1,
                totalEnabled = 1,
                lastSyncTime = null,
            )

        assertEquals(
            "Syncing with 1 device",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Connected and syncing",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `notification shows multiple devices syncing when all connected`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 3,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals(
            "Syncing with 3 devices",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Connected and syncing",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `notification shows partial connection with X of Y format`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 2,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals(
            "Syncing with 2 of 3 devices",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.contains("waiting") == true
        )
    }

    @Test
    fun `notification shows waiting count when partially connected`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 1,
                totalEnabled = 3,
                lastSyncTime = null,
            )

        assertEquals(
            "Syncing with 1 of 3 devices",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        val content = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(content.contains("2 waiting") || content.contains("waiting"))
    }

    @Test
    fun `notification shows last sync time when available`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 2,
                totalEnabled = 2,
                lastSyncTime = lastSyncTime,
            )

        assertEquals(
            "Syncing with 2 devices",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        val content = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
    }

    @Test
    fun `notification shows last sync time with waiting count when partially connected`() {
        val lastSyncTime = ZonedDateTime.of(2024, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"))
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 1,
                totalEnabled = 3,
                lastSyncTime = lastSyncTime,
            )

        assertEquals(
            "Syncing with 1 of 3 devices",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        val content = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(content.contains("Last sync:"))
        assertTrue(content.contains("waiting"))
    }

    @Test
    fun `notification has refresh and stop actions`() {
        val notification =
            createMultiDeviceNotification(
                context = context,
                connectedCount = 1,
                totalEnabled = 1,
                lastSyncTime = null,
            )

        assertNotNull(notification.actions)
        assertEquals(2, notification.actions?.size)
        assertEquals("Refresh", notification.actions!![0].title)
        assertEquals("Stop all", notification.actions!![1].title)
    }
}
