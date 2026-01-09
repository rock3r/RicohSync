package dev.sebastiano.camerasync.di

import dev.zacsweers.metro.createGraph

/** Factory for creating the test dependency graph. */
object TestGraphFactory {
    /** Creates the test dependency graph with fake implementations. */
    fun create(): TestGraph = createGraph<TestGraph>()
}
