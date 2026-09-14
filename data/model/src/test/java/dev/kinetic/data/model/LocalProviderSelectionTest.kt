package dev.kinetic.data.model

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.model.*
import dev.kinetic.core.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.time.Instant
import org.junit.Test
import kotlin.test.*

class LocalProviderSelectionTest {
    private class NoCredentialsStore : ProviderSettingsStore {
        override val settings = MutableStateFlow(ProviderSettingsSnapshot(mode = ProviderMode.LOCAL_SIMULATED))
        override suspend fun apiKey(): String? = error("Compatible credential path must not be reached")
        override suspend fun openAiApiKey(): String? = error("OpenAI credential path must not be reached")
        override suspend fun setMode(mode: ProviderMode) { settings.value = settings.value.copy(mode = mode) }
        override suspend fun saveConfiguration(configuration: CloudProviderConfiguration, newApiKey: String?) = error("Not allowed")
        override suspend fun replaceApiKey(apiKey: String) = error("Not allowed")
        override suspend fun clearApiKey() = error("Not allowed")
        override suspend fun setAutomaticContextCompactionEnabled(enabled: Boolean) = error("Not allowed")
    }

    private fun request(text: String) = ModelRequest("r", "t", "s", listOf(
        AgentMessage("m", "t", MessageRole.USER, text, Instant.EPOCH, 1)), listOf(EchoTool().definition))

    @Test fun `selected local stream and continuation never use HTTP or either credential`() = runTest {
        var httpCalls = 0
        val client = OkHttpClient.Builder().addInterceptor { httpCalls++; error("HTTP forbidden in local test") }.build()
        val store = NoCredentialsStore()
        val configured = ConfiguredModelProvider(store, client = client, localProvider = SimulatedLocalModelProvider(0))
        assertEquals(ProviderKind.LOCAL, configured.capabilities().providerKind)
        assertEquals(4096, configured.capabilities().maxContextTokens)
        val request = request("echo: hello")
        val response = configured.stream(request).toList().filterIsInstance<ModelStreamEvent.Completed>().single().response
        val call = response.toolCalls.single()
        // Switching configuration cannot switch the pinned continuation to a paid provider.
        store.setMode(ProviderMode.OPENAI)
        val events = configured.streamContinuation(ModelContinuationRequest(request, response,
            ToolResult.Success(call.callId, call.toolId, EchoOutput("hello")))).toList()
        assertTrue(events.filterIsInstance<ModelStreamEvent.Completed>().single().response.content.contains("hello"))
        configured.finishTurn("t")
        assertEquals(0, httpCalls)
        assertTrue(configured.metrics.records.value.isEmpty())
    }

    @Test fun `local failure cannot fall back to a cloud provider`() = runTest {
        val configured = ConfiguredModelProvider(NoCredentialsStore(), localProvider = SimulatedLocalModelProvider(0))
        assertEquals(ModelProviderFailureKind.LOCAL_UNAVAILABLE,
            assertFailsWith<ModelProviderException> { configured.generate(request("local unavailable")) }.kind)
    }

    @Test fun `real local selection cannot access credentials HTTP or fake fallback`() = runTest {
        val store = NoCredentialsStore()
        store.setMode(ProviderMode.LOCAL_LLAMA)
        val data = object : LocalModelData {
            override fun isInstalled() = true
            override suspend fun <T> withVerifiedModel(block: suspend (java.io.File) -> T): T =
                block(java.io.File("fixture.gguf"))
        }
        val real = LlamaLocalModelProvider(data, LocalTextEngine { _, _ ->
            kotlinx.coroutines.flow.flowOf("actual adapter fixture".toByteArray()) })
        val configured = ConfiguredModelProvider(store, realLocalProvider = real,
            client = OkHttpClient.Builder().addInterceptor { error("No HTTP") }.build())
        assertEquals(1024, configured.capabilities().maxContextTokens)
        assertEquals(ModelToolSupport.Unavailable, configured.toolSupport())
        assertEquals("actual adapter fixture", configured.generate(request("hello")).content)
        val summaryRequest = dev.kinetic.core.context.SummaryGenerationRequest("s", null, request("hello").messages)
        assertEquals("actual adapter fixture", configured.generateSummary(summaryRequest))
        val unavailable = ConfiguredModelProvider(store)
        assertFailsWith<dev.kinetic.core.context.SummaryGenerationException> { unavailable.generateSummary(summaryRequest) }
        assertEquals(ModelProviderFailureKind.LOCAL_UNAVAILABLE,
            assertFailsWith<ModelProviderException> { unavailable.generate(request("hello")) }.kind)
    }
}
