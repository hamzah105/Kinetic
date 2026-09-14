package dev.kinetic.data.model

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PackagedLlamaEngineTest {
    @Test fun `summary requires native end of generation and closes on output limit`() = runBlocking {
        for (completed in listOf(false, true)) {
            val closes = AtomicInteger()
            val engine = PackagedLlamaEngine { object : NativeCalls {
                override fun create() = 1L
                override fun load(id: Long, modelPath: String, prompt: ByteArray) = Unit
                override fun next(id: Long): ByteArray? = null
                override fun completed(id: Long) = completed
                override fun cancel(id: Long) = Unit
                override fun close(id: Long) { closes.incrementAndGet() }
            } }
            if (completed) engine.streamSummary(File("fixture"), "summary").toList()
            else assertEquals("OUTPUT_LIMIT", assertFailsWith<IllegalStateException> {
                engine.streamSummary(File("fixture"), "summary").toList()
            }.message)
            assertEquals(1, closes.get())
        }
    }
    @Test fun `cancellation immediately after allocation cannot leak a native handle`() = runBlocking {
        val closes = AtomicInteger()
        lateinit var job: Job
        val engine = PackagedLlamaEngine { object : NativeCalls {
            override fun create(): Long { job.cancel(); return 1L }
            override fun load(id: Long, modelPath: String, prompt: ByteArray) = error("cancelled before load")
            override fun next(id: Long): ByteArray? = error("not reached")
            override fun cancel(id: Long) = Unit
            override fun close(id: Long) { closes.incrementAndGet() }
        } }
        job = launch(start = CoroutineStart.LAZY) { engine.stream(File("fixture"), "hello").toList() }
        job.start()
        withTimeout(20_000) { job.join() }
        assertEquals(1, closes.get())
    }

    @Test fun `cancel during load signals native and joins close before reuse`() = runBlocking {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val closes = AtomicInteger()
        val native = object : NativeCalls {
            override fun create() = 1L
            override fun load(id: Long, modelPath: String, prompt: ByteArray) {
                started.countDown()
                check(stopped.await(20, TimeUnit.SECONDS))
            }
            override fun next(id: Long): ByteArray? = null
            override fun cancel(id: Long) { stopped.countDown() }
            override fun close(id: Long) { closes.incrementAndGet() }
        }
        val engine = PackagedLlamaEngine { native }
        val job = launch { engine.stream(File("fixture"), "hello").toList() }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(20, TimeUnit.SECONDS) })
            withTimeout(20_000) { job.cancelAndJoin() }
            assertEquals(1, closes.get())
            engine.stream(File("fixture"), "explicit retry").toList()
            assertEquals(2, closes.get())
        } finally { job.cancelAndJoin() }
    }

    @Test fun `native load failure closes allocated session exactly once`() = runBlocking {
        val closes = AtomicInteger()
        val engine = PackagedLlamaEngine { object : NativeCalls {
            override fun create() = 1L
            override fun load(id: Long, modelPath: String, prompt: ByteArray) { error("fixture failure") }
            override fun next(id: Long): ByteArray? = error("not reached")
            override fun cancel(id: Long) = Unit
            override fun close(id: Long) { closes.incrementAndGet() }
        } }
        assertFailsWith<IllegalStateException> { engine.stream(File("fixture"), "hello").toList() }
        assertEquals(1, closes.get())
    }
}
