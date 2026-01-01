package dev.sebastiano.ricohsync.devicesync

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TAG = "SyncErrorVibrator"

/**
 * Handles vibration notifications for sync errors.
 *
 * It provides an aggressive vibration pattern designed to be felt in a pocket
 * and implements a 5-minute cooldown between vibrations.
 */
class SyncErrorVibrator(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Aggressive vibration pattern for pocket detection:
     * Vibrate 500ms, pause 200ms, repeat 3 times.
     */
    private val aggressivePattern = longArrayOf(0, 500, 200, 500, 200, 500)
    private val aggressiveAmplitudes = intArrayOf(0, 255, 0, 255, 0, 255)

    /**
     * Triggers an aggressive vibration if the 5-minute cooldown has passed.
     */
    fun vibrate() {
        val now = Instant.now()
        val lastTime = lastVibrationTime

        if (lastTime != null && ChronoUnit.MINUTES.between(lastTime, now) < 5) {
            Log.d(TAG, "Skipping vibration, last one was less than 5 minutes ago")
            return
        }

        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device does not have a vibrator")
            return
        }

        Log.i(TAG, "Starting aggressive vibration for sync error")
        lastVibrationTime = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(aggressivePattern, aggressiveAmplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(aggressivePattern, -1)
        }
    }

    /**
     * Stops any ongoing vibration.
     */
    fun stop() {
        Log.d(TAG, "Stopping vibration")
        vibrator.cancel()
    }

    companion object {
        private var lastVibrationTime: Instant? = null
    }
}

