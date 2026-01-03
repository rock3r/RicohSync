package dev.sebastiano.ricohsync.fakes

import com.juul.khronicle.Logger
import com.juul.khronicle.ReadMetadata

/** Fake Khronicle logger for testing. Logs to stdout. */
object FakeKhronicleLogger : Logger {
    override fun verbose(
        tag: String,
        message: String,
        metadata: ReadMetadata,
        throwable: Throwable?,
    ) {
        println("[$tag] VERBOSE: $message${if (throwable != null) ", $throwable" else ""}")
    }

    override fun debug(
        tag: String,
        message: String,
        metadata: ReadMetadata,
        throwable: Throwable?,
    ) {
        println("[$tag] DEBUG: $message${if (throwable != null) ", $throwable" else ""}")
    }

    override fun info(tag: String, message: String, metadata: ReadMetadata, throwable: Throwable?) {
        println("[$tag] INFO: $message${if (throwable != null) ", $throwable" else ""}")
    }

    override fun warn(tag: String, message: String, metadata: ReadMetadata, throwable: Throwable?) {
        println("[$tag] WARN: $message${if (throwable != null) ", $throwable" else ""}")
    }

    override fun error(
        tag: String,
        message: String,
        metadata: ReadMetadata,
        throwable: Throwable?,
    ) {
        println("[$tag] ERROR: $message${if (throwable != null) ", $throwable" else ""}")
    }

    override fun assert(
        tag: String,
        message: String,
        metadata: ReadMetadata,
        throwable: Throwable?,
    ) {
        println("[$tag] ASSERT: $message${if (throwable != null) ", $throwable" else ""}")
    }
}
