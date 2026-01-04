package dev.sebastiano.ricohsync.di

import android.app.Application
import android.content.Context
import dev.sebastiano.ricohsync.data.repository.DataStorePairedDevicesRepository
import dev.sebastiano.ricohsync.data.repository.FusedLocationRepository
import dev.sebastiano.ricohsync.data.repository.KableCameraRepository
import dev.sebastiano.ricohsync.data.repository.pairedDevicesDataStoreV2
import dev.sebastiano.ricohsync.devicesync.AndroidIntentFactory
import dev.sebastiano.ricohsync.devicesync.AndroidNotificationBuilder
import dev.sebastiano.ricohsync.devicesync.AndroidPendingIntentFactory
import dev.sebastiano.ricohsync.devicesync.DefaultLocationCollector
import dev.sebastiano.ricohsync.devicesync.IntentFactory
import dev.sebastiano.ricohsync.devicesync.MultiDeviceSyncService
import dev.sebastiano.ricohsync.devicesync.NotificationBuilder
import dev.sebastiano.ricohsync.devicesync.PendingIntentFactory
import dev.sebastiano.ricohsync.domain.repository.CameraRepository
import dev.sebastiano.ricohsync.domain.repository.LocationRepository
import dev.sebastiano.ricohsync.domain.repository.PairedDevicesRepository
import dev.sebastiano.ricohsync.domain.vendor.CameraVendorRegistry
import dev.sebastiano.ricohsync.domain.vendor.DefaultCameraVendorRegistry
import dev.sebastiano.ricohsync.vendors.ricoh.RicohCameraVendor
import dev.sebastiano.ricohsync.vendors.sony.SonyCameraVendor
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope

/**
 * Metro dependency graph for production dependencies.
 *
 * The graph is created via the Factory interface using createGraphFactory, which allows passing
 * external dependencies like Application. Context is then provided from Application and injected
 * into @Provides methods that need it.
 */
@DependencyGraph
interface AppGraph {
    val vendorRegistry: CameraVendorRegistry
    val notificationBuilder: NotificationBuilder
    val intentFactory: IntentFactory
    val pendingIntentFactory: PendingIntentFactory
    val pairedDevicesRepository: PairedDevicesRepository
    val locationRepository: LocationRepository
    val cameraRepository: CameraRepository

    @Provides fun provideApplicationContext(application: Application): Context = application

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
    @Provides
    fun provideVendorRegistry(): CameraVendorRegistry =
        DefaultCameraVendorRegistry(
            vendors =
                listOf(
                    RicohCameraVendor,
                    SonyCameraVendor,
                    // Add more vendors here:
                    // CanonCameraVendor,
                    // NikonCameraVendor,
                )
        )

    @Provides
    fun providePairedDevicesRepository(context: Context): PairedDevicesRepository =
        DataStorePairedDevicesRepository(context.pairedDevicesDataStoreV2)

    @Provides
    fun provideLocationRepository(context: Context): LocationRepository =
        FusedLocationRepository(context)

    @Provides
    fun provideCameraRepository(vendorRegistry: CameraVendorRegistry): CameraRepository =
        KableCameraRepository(vendorRegistry = vendorRegistry)

    @Provides
    fun provideLocationCollector(
        locationRepository: LocationRepository,
        coroutineScope: CoroutineScope,
    ): dev.sebastiano.ricohsync.devicesync.LocationCollector =
        DefaultLocationCollector(
            locationRepository = locationRepository,
            coroutineScope = coroutineScope,
        )

    @Provides
    fun provideNotificationBuilder(context: Context): NotificationBuilder =
        AndroidNotificationBuilder(context)

    @Provides
    fun provideIntentFactory(): IntentFactory =
        AndroidIntentFactory(MultiDeviceSyncService::class.java)

    @Provides
    fun providePendingIntentFactory(): PendingIntentFactory = AndroidPendingIntentFactory()

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
