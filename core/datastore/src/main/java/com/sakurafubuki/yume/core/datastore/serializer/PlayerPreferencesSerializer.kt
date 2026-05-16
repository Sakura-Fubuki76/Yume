package com.sakurafubuki.yume.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.sakurafubuki.yume.core.model.PlayerPreferences
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object PlayerPreferencesSerializer : Serializer<PlayerPreferences> {

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override val defaultValue: PlayerPreferences
        get() = PlayerPreferences()

    override suspend fun readFrom(input: InputStream): PlayerPreferences {
        try {
            val raw = input.readBytes().decodeToString()
                .replace("\"AAUDIO\"", "\"AAUDIO_LOW_LATENCY\"")
                .replace("\"OPENSL_ES\"", "\"AUDIO_TRACK\"")
            return jsonFormat.decodeFromString(
                deserializer = PlayerPreferences.serializer(),
                string = raw,
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read datastore", exception)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: PlayerPreferences, output: OutputStream) {
        output.write(
            jsonFormat.encodeToString(
                serializer = PlayerPreferences.serializer(),
                value = t,
            ).encodeToByteArray(),
        )
    }
}
