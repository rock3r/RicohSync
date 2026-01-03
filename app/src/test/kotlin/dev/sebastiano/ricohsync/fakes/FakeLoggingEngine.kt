package dev.sebastiano.ricohsync.fakes

import com.juul.kable.logs.LogEngine
import com.juul.kable.logs.Logging

/** Fake logging engine for testing. Logs to stdout. */
object FakeLoggingEngine : LogEngine {
    override fun verbose(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] VERBOSE: $message${if(throwable != null) ", $throwable" else ""}")
    }

    override fun debug(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] DEBUG: $message${if(throwable != null) ", $throwable" else ""}")
    }

    override fun info(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] INFO: $message${if(throwable != null) ", $throwable" else ""}")
    }

    override fun warn(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] WARN: $message${if(throwable != null) ", $throwable" else ""}")
    }

    override fun error(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] ERROR: $message${if(throwable != null) ", $throwable" else ""}")
    }

    override fun assert(throwable: Throwable?, tag: String, message: String) {
        println("[$tag] ASSERT: $message${if(throwable != null) ", $throwable" else ""}")
    }
}

