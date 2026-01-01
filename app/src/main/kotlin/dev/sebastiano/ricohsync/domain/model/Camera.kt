package dev.sebastiano.ricohsync.domain.model

import dev.sebastiano.ricohsync.domain.vendor.CameraVendor

/**
 * Represents a camera that can be discovered and connected to.
 *
 * This is a domain model decoupled from the Kable library's Advertisement class. Supports cameras
 * from multiple vendors (Ricoh, Canon, Nikon, etc.).
 */
data class Camera(
    val identifier: String,
    val name: String?,
    val macAddress: String,
    val vendor: CameraVendor,
)

/**
 * Type alias for backward compatibility.
 *
 * @deprecated Use [Camera] instead.
 */
@Deprecated(
    message = "Use Camera instead",
    replaceWith = ReplaceWith("Camera", "dev.sebastiano.ricohsync.domain.model.Camera"),
)
typealias RicohCamera = Camera
