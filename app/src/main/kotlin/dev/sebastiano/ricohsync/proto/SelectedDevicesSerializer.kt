package dev.sebastiano.ricohsync.proto

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object SelectedDevicesSerializer : Serializer<SelectedDevices> {
    override val defaultValue: SelectedDevices = SelectedDevices.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): SelectedDevices {
        try {
            return SelectedDevices.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read paired devices proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: SelectedDevices,
        output: OutputStream
    ) = t.writeTo(output)
}

val Context.pairedDevicesDataStore: DataStore<SelectedDevices> by dataStore(
    fileName = "selected-devices.pb",
    serializer = SelectedDevicesSerializer
)
