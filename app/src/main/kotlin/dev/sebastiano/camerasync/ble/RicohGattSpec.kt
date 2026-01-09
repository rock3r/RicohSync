@file:Suppress("DEPRECATION")

package dev.sebastiano.camerasync.ble

/**
 * Backward compatibility typealias for RicohGattSpec.
 *
 * @deprecated This class has been moved to vendors.ricoh package. Use
 *   dev.sebastiano.camerasync.vendors.ricoh.RicohGattSpec instead.
 */
@Deprecated(
    message = "Moved to vendors.ricoh package",
    replaceWith =
        ReplaceWith(
            "dev.sebastiano.camerasync.vendors.ricoh.RicohGattSpec",
            "dev.sebastiano.camerasync.vendors.ricoh.RicohGattSpec",
        ),
    level = DeprecationLevel.WARNING,
)
typealias RicohGattSpec = dev.sebastiano.camerasync.vendors.ricoh.RicohGattSpec
