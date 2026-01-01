@file:Suppress("DEPRECATION")

package dev.sebastiano.ricohsync.ble

/**
 * Backward compatibility typealias for RicohGattSpec.
 *
 * @deprecated This class has been moved to vendors.ricoh package. Use
 *   dev.sebastiano.ricohsync.vendors.ricoh.RicohGattSpec instead.
 */
@Deprecated(
    message = "Moved to vendors.ricoh package",
    replaceWith =
        ReplaceWith(
            "dev.sebastiano.ricohsync.vendors.ricoh.RicohGattSpec",
            "dev.sebastiano.ricohsync.vendors.ricoh.RicohGattSpec",
        ),
    level = DeprecationLevel.WARNING,
)
typealias RicohGattSpec = dev.sebastiano.ricohsync.vendors.ricoh.RicohGattSpec
