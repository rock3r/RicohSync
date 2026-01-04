package dev.sebastiano.ricohsync.di

import dev.sebastiano.ricohsync.devicesync.IntentFactory
import dev.sebastiano.ricohsync.devicesync.NotificationBuilder
import dev.sebastiano.ricohsync.devicesync.PendingIntentFactory
import dev.sebastiano.ricohsync.fakes.FakeIntentFactory
import dev.sebastiano.ricohsync.fakes.FakeNotificationBuilder
import dev.sebastiano.ricohsync.fakes.FakePendingIntentFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

/** Metro dependency graph for test dependencies using fake implementations. */
@DependencyGraph
interface TestGraph {
    val notificationBuilder: NotificationBuilder
    val intentFactory: IntentFactory
    val pendingIntentFactory: PendingIntentFactory

    @Provides fun provideNotificationBuilder(): NotificationBuilder = FakeNotificationBuilder()

    @Provides fun provideIntentFactory(): IntentFactory = FakeIntentFactory()

    @Provides fun providePendingIntentFactory(): PendingIntentFactory = FakePendingIntentFactory()
}
