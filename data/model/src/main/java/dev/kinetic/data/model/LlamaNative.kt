package dev.kinetic.data.model

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** All native entry points reside in the signed APK. No path-based System.load or downloaded code. */
internal interface NativeCalls {
    fun create(): Long
    fun load(id: Long, modelPath: String, prompt: ByteArray)
    fun next(id: Long): ByteArray?
    fun cancel(id: Long)
    fun close(id: Long)
    fun completed(id: Long): Boolean = true
}

internal class LlamaNative : NativeCalls {
    external override fun create(): Long
    external override fun load(id: Long, modelPath: String, prompt: ByteArray)
    external override fun next(id: Long): ByteArray?
    external override fun cancel(id: Long)
    external override fun close(id: Long)
    external override fun completed(id: Long): Boolean
    companion object {
        val instance: LlamaNative by lazy { System.loadLibrary("kinetic_local"); LlamaNative() }
    }
}

class PackagedLlamaEngine internal constructor(private val nativeFactory: () -> NativeCalls) : LocalTextEngine {
    constructor() : this({ LlamaNative.instance })
    override fun stream(model: File, prompt: String): Flow<ByteArray> = flow {
        // One native model/context in this process; cancellation joins teardown before next request.
        gate.withLock { emitAll(runNative(model, prompt)) }
    }
    override fun streamSummary(model: File, prompt: String): Flow<ByteArray> = flow {
        gate.withLock { emitAll(runNative(model, prompt, requireCompletion = true)) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runNative(model: File, prompt: String, requireCompletion: Boolean = false): Flow<ByteArray> = channelFlow {
        val native = nativeFactory()
        val id = native.create()
        // ATOMIC installs cleanup even if cancellation races scheduling. ensureActive precedes
        // native load; only handle cleanup runs after cancellation, never lifecycle-bound UI work.
        launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            try {
                ensureActive()
                native.load(id, model.absolutePath, prompt.toByteArray(Charsets.UTF_8))
                while (true) {
                    ensureActive()
                    val bytes = native.next(id) ?: break
                    send(bytes)
                }
                if (requireCompletion && !native.completed(id)) error("OUTPUT_LIMIT")
                close()
            } catch (failure: Throwable) { close(failure)
            } finally { native.close(id) }
        }
        // Registry-held shared ownership makes cancel versus close safe; no raw freed JNI pointer.
        awaitClose { native.cancel(id) }
    }.buffer(0)

    companion object { private val gate = Mutex() }
}
