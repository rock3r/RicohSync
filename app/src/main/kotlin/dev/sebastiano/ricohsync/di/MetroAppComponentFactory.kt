package dev.sebastiano.ricohsync.di

import android.app.Application
import androidx.annotation.Keep
import androidx.core.app.AppComponentFactory

/**
 * An [AppComponentFactory] that uses Metro for dependency injection.
 *
 * Currently, Activities access dependencies via the Application instance rather than constructor
 * injection. This factory is kept for potential future use with constructor injection.
 */
@Keep
class MetroAppComponentFactory : AppComponentFactory() {

    override fun instantiateApplicationCompat(cl: ClassLoader, className: String): Application {
        return super.instantiateApplicationCompat(cl, className)
    }
}
