package dev.sebastiano.camerasync.ble

import com.juul.kable.Advertisement

/**
 * Builds a manufacturer data map from the advertisement.
 *
 * Kable's [Advertisement.manufacturerData] only provides the first manufacturer data entry. This
 * helper converts it into the map format expected by our vendor implementations.
 */
fun Advertisement.buildManufacturerDataMap(): Map<Int, ByteArray> {
    val mfrData = manufacturerData ?: return emptyMap()
    return mapOf(mfrData.code to mfrData.data)
}
