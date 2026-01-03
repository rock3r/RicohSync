package dev.sebastiano.ricohsync

import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.Log
import com.juul.khronicle.Logger
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.ricohsync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.ricohsync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.ricohsync.vendors.sony.SonyCameraVendor

/** Application-level configuration and dependency creation for RicohSync. */
object RicohSyncApp {

    /**
     * Initializes Khronicle logging with the provided logger.
     *
     * @param logger The logger to use. Defaults to ConsoleLogger for production.
     */
    fun initializeLogging(logger: Logger = ConsoleLogger) {
        Log.dispatcher.install(logger)
    }

    /**
     * Creates the default camera vendor registry with all supported vendors.
     *
     * Currently supports:
     * - Ricoh cameras (GR IIIx, GR III, etc.)
     * - Sony Alpha cameras (via DI Remote Control protocol)
     *
     * To add support for additional camera vendors:
     * 1. Create a new vendor package (e.g., vendors/canon/)
     * 2. Implement CameraVendor, CameraGattSpec, and CameraProtocol
     * 3. Add the vendor to this list
     */
    fun createVendorRegistry(): CameraVendorRegistry {
        return DefaultCameraVendorRegistry(
            vendors =
                listOf(
                    RicohCameraVendor,
                    SonyCameraVendor,
                    // Add more vendors here:
                    // CanonCameraVendor,
                    // NikonCameraVendor,
                )
        )
    }
}
