package com.wakeup.launcher.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A single persisted document with an observable value.
 * Writes are atomic (temp file then rename) so a crash never leaves a half-written file, and a corrupt
 * file is set aside rather than crashing the launcher.
 */
class JsonStore<T : Any>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val scope: CoroutineScope,
    private val default: () -> T,
    private val json: Json = DefaultJson,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<T> get() = _state
    val value: T get() = _state.value

    private fun load(): T {
        if (!file.exists()) return default()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "corrupt ${file.name}, setting aside: ${e.javaClass.simpleName}")
            runCatching { file.renameTo(File(file.parentFile, file.name + ".corrupt")) }
            default()
        }
    }

    fun update(transform: (T) -> T) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        scope.launch(Dispatchers.IO) { mutex.withLock { write(_state.value) } }
    }

    fun set(next: T) = update { next }

    private fun write(v: T) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, v))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: Exception) {
            Log.w(TAG, "could not save ${file.name}: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "JsonStore"
        val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    }
}
