package dev.sebastiano.ricohsync

import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.ricohsync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.ricohsync.vendors.ricoh.RicohCameraVendor

/**
 * Application-level configuration and dependency creation for RicohSync.
 */
object RicohSyncApp {

    /**
     * Creates the default camera vendor registry with all supported vendors.
     *
     * Currently supports:
     * - Ricoh cameras (GR IIIx, GR III, etc.)
     *
     * To add support for additional camera vendors:
     * 1. Create a new vendor package (e.g., vendors/canon/)
     * 2. Implement CameraVendor, CameraGattSpec, and CameraProtocol
     * 3. Add the vendor to this list
     */
    fun createVendorRegistry(): CameraVendorRegistry {
        return DefaultCameraVendorRegistry(
            vendors = listOf(
                RicohCameraVendor,
                // Add more vendors here:
                // CanonCameraVendor,
                // NikonCameraVendor,
                // SonyCameraVendor,
            ),
        )
    }
}
