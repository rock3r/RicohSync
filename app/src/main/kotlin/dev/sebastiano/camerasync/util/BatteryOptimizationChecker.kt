package dev.sebastiano.camerasync.util

import android.content.Context
import com.juul.khronicle.Log

/**
 * Interface for checking battery optimization status. This allows the implementation to be mocked
 * in tests.
 */
interface BatteryOptimizationChecker {
    /**
     * Checks if the app is currently ignoring battery optimizations.
     *
     * @param context The application context
     * @return true if the app is ignoring battery optimizations, false otherwise
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean
}

/** Production implementation that uses the Android PowerManager. */
class AndroidBatteryOptimizationChecker : BatteryOptimizationChecker {
    private val logTag = javaClass.simpleName

    override fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        Log.debug(logTag) {
            "Checking battery optimization status via AndroidBatteryOptimizationChecker"
        }
        return BatteryOptimizationUtil.isIgnoringBatteryOptimizations(context)
    }
}
