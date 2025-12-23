package dev.sebastiano.ricohsync.ble

import java.util.UUID

/**
 * GATT specification for Ricoh cameras.
 *
 * Contains all known service and characteristic UUIDs used for communication with Ricoh cameras
 * over Bluetooth Low Energy.
 */
object RicohGattSpec {

    /** Service UUID used for scanning and filtering Ricoh camera advertisements. */
    val SCAN_FILTER_SERVICE_UUID: UUID =
        UUID.fromString("84A0DD62-E8AA-4D0F-91DB-819B6724C69E")

    /** Firmware version service and characteristic. */
    object Firmware {
        val SERVICE_UUID: UUID = UUID.fromString("9a5ed1c5-74cc-4c50-b5b6-66a48e7ccff1")
        val VERSION_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("b4eb8905-7411-40a6-a367-2834c2157ea7")
    }

    /** Paired device name service and characteristic. */
    object DeviceName {
        val SERVICE_UUID: UUID = UUID.fromString("0f291746-0c80-4726-87a7-3c501fd3b4b6")
        val NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("fe3a32f8-a189-42de-a391-bc81ae4daa76")
    }

    /** Date/time and geo-tagging service and characteristics. */
    object DateTime {
        val SERVICE_UUID: UUID = UUID.fromString("4b445988-caa0-4dd3-941d-37b4f52aca86")
        val DATE_TIME_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("fa46bbdd-8a8f-4796-8cf3-aa58949b130a")
        val GEO_TAGGING_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("a36afdcf-6b67-4046-9be7-28fb67dbc071")
    }

    /** Location sync service and characteristic. */
    object Location {
        val SERVICE_UUID: UUID = UUID.fromString("84a0dd62-e8aa-4d0f-91db-819b6724c69e")
        val LOCATION_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("28f59d60-8b8e-4fcd-a81f-61bdb46595a9")
    }
}
