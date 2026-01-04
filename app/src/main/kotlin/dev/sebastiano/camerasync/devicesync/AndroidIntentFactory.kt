package dev.sebastiano.camerasync.devicesync

import android.content.Context
import android.content.Intent

/** Android implementation of [IntentFactory] for MultiDeviceSyncService. */
class AndroidIntentFactory(private val serviceClass: Class<*>) : IntentFactory {
    override fun createRefreshIntent(context: Context): Intent =
        Intent(context, serviceClass).apply { action = MultiDeviceSyncService.ACTION_REFRESH }

    override fun createStopIntent(context: Context): Intent =
        Intent(context, serviceClass).apply { action = MultiDeviceSyncService.ACTION_STOP }

    override fun createStartIntent(context: Context): Intent = Intent(context, serviceClass)
}
