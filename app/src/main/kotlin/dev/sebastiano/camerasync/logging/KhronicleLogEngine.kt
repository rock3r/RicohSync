package dev.sebastiano.camerasync.logging

import com.juul.kable.logs.LogEngine
import com.juul.khronicle.Log

/** Adapter that implements Kable's LogEngine interface using Khronicle for logging. */
object KhronicleLogEngine : LogEngine {
    override fun verbose(throwable: Throwable?, tag: String, message: String) {
        Log.verbose(tag = tag, throwable = throwable) { message }
    }

    override fun debug(throwable: Throwable?, tag: String, message: String) {
        Log.debug(tag = tag, throwable = throwable) { message }
    }

    override fun info(throwable: Throwable?, tag: String, message: String) {
        Log.info(tag = tag, throwable = throwable) { message }
    }

    override fun warn(throwable: Throwable?, tag: String, message: String) {
        Log.warn(tag = tag, throwable = throwable) { message }
    }

    override fun error(throwable: Throwable?, tag: String, message: String) {
        Log.error(tag = tag, throwable = throwable) { message }
    }

    override fun assert(throwable: Throwable?, tag: String, message: String) {
        Log.assert(tag = tag, throwable = throwable) { message }
    }
}
