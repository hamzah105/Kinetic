package dev.kinetic.data.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object QwenCandidate {
    const val FILE = "Qwen3-0.6B-Q4_0.gguf"
    const val BYTES = 428_970_080L
    const val SHA256 = "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4"
    const val REVISION = "b5f37287796e5be0ea3dab2e7430873fb3f73e49"
}

/** App-private DATA only. No URL, executable path, credential or arbitrary model support. */
interface LocalModelData {
    fun isInstalled(): Boolean
    suspend fun <T> withVerifiedModel(block: suspend (File) -> T): T
}

enum class LocalModelState { NOT_INSTALLED, UNVERIFIED, VERIFYING, READY, INVALID, ERROR, WAITING_TO_DELETE }

class VerifiedLocalModel internal constructor(
    directory: File,
    private val expectedBytes: Long,
    private val expectedHash: String,
    private val freeSpace: (File) -> Long,
) : LocalModelData {
    constructor(directory: File) : this(directory, QwenCandidate.BYTES, QwenCandidate.SHA256, { it.usableSpace })
    private val directory = directory.canonicalFile
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(if (isInstalled()) LocalModelState.UNVERIFIED else LocalModelState.NOT_INSTALLED)
    val state = mutableState.asStateFlow()
    private fun active(): File = File(directory, QwenCandidate.FILE).also {
        check(it.canonicalFile.parentFile == directory && it.canonicalFile == it.absoluteFile)
    }
    override fun isInstalled(): Boolean = runCatching { active().let { it.isFile && it.length() == expectedBytes } }.getOrDefault(false)

    /** Only our fixed staging namespace; never recurse, follow links, or touch sibling app data. */
    private fun cleanPartials() {
        directory.listFiles()?.filter { it.name.matches(Regex("qwen-import-[0-9]+\\.part")) }
            ?.filter { it.isFile && it.canonicalFile == it.absoluteFile }
            ?.forEach { check(it.delete()) }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                cleanPartials()
                if (!active().exists()) mutableState.value = LocalModelState.NOT_INSTALLED
                else verifyActive()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = LocalModelState.INVALID }
        }
    }

    private suspend fun verifyActive() {
        if (!active().exists()) {
            mutableState.value = LocalModelState.NOT_INSTALLED
            error("MODEL_NOT_INSTALLED")
        }
        mutableState.value = LocalModelState.VERIFYING
        try {
            active().inputStream().use { verifyCopy(it, expectedBytes, expectedHash) { _, _ -> } }
            mutableState.value = LocalModelState.READY
        } catch (cancelled: CancellationException) {
            mutableState.value = LocalModelState.UNVERIFIED
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = LocalModelState.INVALID
            throw failure
        }
    }

    /** The same lease covers native generation including structured child teardown. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        mutableState.value = LocalModelState.WAITING_TO_DELETE
        try {
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                val target = active()
                check(!target.exists() || target.delete())
                mutableState.value = LocalModelState.NOT_INSTALLED
                cleanPartials()
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = if (isInstalled()) LocalModelState.UNVERIFIED else LocalModelState.NOT_INSTALLED
            throw cancelled
        } catch (failure: Exception) { mutableState.value = LocalModelState.ERROR; throw failure }
    }

    suspend fun import(open: () -> InputStream) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(directory.isDirectory || directory.mkdirs())
            cleanPartials()
            if (freeSpace(directory) < expectedBytes + 256L * 1024 * 1024) {
                mutableState.value = LocalModelState.ERROR
                error("Insufficient model storage")
            }
            mutableState.value = LocalModelState.VERIFYING
            val temporary = File.createTempFile("qwen-import-", ".part", directory)
            try {
                open().use { input ->
                    FileOutputStream(temporary).use { output ->
                        verifyCopy(input, expectedBytes, expectedHash) { bytes, count -> output.write(bytes, 0, count) }
                        output.fd.sync()
                    }
                }
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), active().toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                mutableState.value = LocalModelState.READY
            } catch (failure: Exception) {
                mutableState.value = if (isInstalled()) LocalModelState.UNVERIFIED else LocalModelState.ERROR
                throw failure
            } finally { temporary.delete() }
        }
    }

    override suspend fun <T> withVerifiedModel(block: suspend (File) -> T): T = mutex.withLock {
        val model = active()
        withContext(Dispatchers.IO) { verifyActive() }
        block(model)
    }
}

/** Bounded size and digest verification also runs before every native model load. */
internal suspend fun verifyCopy(
    input: InputStream,
    expectedBytes: Long = QwenCandidate.BYTES,
    expectedHash: String = QwenCandidate.SHA256,
    sink: (ByteArray, Int) -> Unit,
) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= expectedBytes) { "Model size mismatch" }
        digest.update(buffer, 0, count)
        sink(buffer, count)
    }
    require(total == expectedBytes) { "Model size mismatch" }
    val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    require(hash == expectedHash) { "Model digest mismatch" }
}
