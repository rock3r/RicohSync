package dev.sebastiano.ricohsync.domain.model

/**
 * Represents a Ricoh camera that can be discovered and connected to.
 *
 * This is a domain model decoupled from the Kable library's Advertisement class.
 */
data class RicohCamera(
    val identifier: String,
    val name: String?,
    val macAddress: String,
)
