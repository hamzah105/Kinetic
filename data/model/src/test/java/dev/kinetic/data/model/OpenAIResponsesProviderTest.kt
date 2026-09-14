package dev.kinetic.data.model

import dev.kinetic.core.agent.*
import dev.kinetic.core.model.*
import dev.kinetic.core.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.*

class OpenAIResponsesProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCert = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverCert.sslSocketFactory()); start() }
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
    }
    @After fun close() { server.close() }
    private fun provider(config: OpenAiConfiguration = OpenAiConfiguration(), metrics: LocalProviderMetrics = LocalProviderMetrics()) =
        OpenAIResponsesProvider(config, "SYNTHETIC_OPENAI_TEST_KEY", client, metrics, server.url("/v1/responses").toString())
    private fun request() = ModelRequest("r", "t", "s", listOf(AgentMessage("m", "t", MessageRole.USER, "Hello", Instant.EPOCH)),
        listOf(EchoTool().definition, ProtectedDemoTool().definition))
    private fun enqueue(body: String) { server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(body).build()) }

    @Test fun direct_mapping_is_stateless_bounded_low_and_has_no_sampling_or_server_state() {
        val body = Json.parseToJsonElement(provider().requestBody(request())).jsonObject
        assertEquals(ASTRA_MODEL, body["model"]!!.jsonPrimitive.content)
        assertEquals("false", body["store"].toString())
        assertEquals("false", body["background"].toString())
        assertEquals("low", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("1024", body["max_output_tokens"].toString())
        assertEquals("Hello", body["input"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content)
        listOf("temperature", "top_p", "logprobs", "top_logprobs", "previous_response_id", "conversation", "async", "service_tier").forEach {
            assertFalse(body.containsKey(it))
        }
        assertFalse(body.toString().contains("SYNTHETIC_OPENAI_TEST_KEY"))
    }
    @Test fun streamed_text_and_completion_are_normalized_once() = runTest {
        enqueue(textStream("Hello 🌠"))
        val events = provider().stream(request()).toList()
        assertIs<ModelStreamEvent.Started>(events.first())
        assertEquals("Hello 🌠", events.filterIsInstance<ModelStreamEvent.TextDelta>().joinToString("") { it.text })
        assertEquals("Hello 🌠", events.filterIsInstance<ModelStreamEvent.Completed>().single().response.content)
        assertEquals("/v1/responses", server.takeRequest().url.encodedPath)
    }
    @Test fun full_call_reuses_existing_typed_tool_and_correct_call_identity() = runTest {
        enqueue(toolStream())
        val events = provider(OpenAiConfiguration(structuredTools = true)).stream(request()).toList()
        val call = events.filterIsInstance<ModelStreamEvent.Completed>().single().response.toolCalls.single()
        assertEquals("call-1", call.callId); assertEquals("t", call.proposalTurnId); assertEquals(0, call.index)
        assertEquals(EchoInput("OK"), call.input)
        assertEquals(1, events.filterIsInstance<ModelStreamEvent.ToolCallCompleted>().size)
    }
    @Test fun partial_arguments_never_produce_completion() {
        val decoder = ResponsesSseDecoder("t", request().availableTools)
        val events = toolStream().substringBefore("event: response.function_call_arguments.done").lineSequence().flatMap { decoder.acceptLine(it).asSequence() }.toList()
        assertTrue(events.any { it is ModelStreamEvent.ToolArgumentDelta })
        assertFalse(events.any { it is ModelStreamEvent.Completed || it is ModelStreamEvent.ToolCallCompleted })
        assertFailsWith<ModelProviderException> { decoder.finish() }
    }
    @Test fun changed_completed_arguments_fail_closed() = runTest {
        enqueue(toolStream().replace("\\\"text\\\":\\\"OK\\\"", "\\\"text\\\":\\\"CHANGED\\\"").replaceFirst("CHANGED", "OK"))
        assertFailsWith<ModelProviderException> { provider(OpenAiConfiguration(structuredTools = true)).generate(request()) }
    }
    @Test fun duplicate_terminal_event_never_delivers_an_actionable_completion() = runTest {
        val stream = textStream("hi")
        enqueue(stream + stream.substringAfter("event: response.completed").let { "event: response.completed$it" })
        val delivered = mutableListOf<ModelStreamEvent>()
        assertFailsWith<ModelProviderException> { provider().stream(request()).collect { delivered += it } }
        assertTrue(delivered.none { it is ModelStreamEvent.Completed })
    }
    @Test fun malformed_event_is_typed_and_does_not_reflect_body() = runTest {
        enqueue("data: {SYNTHETIC_OPENAI_TEST_KEY\n\n")
        val failure = assertFailsWith<ModelProviderException> { provider().generate(request()) }
        assertEquals(ModelProviderFailureKind.MALFORMED_RESPONSE, failure.kind)
        assertFalse(failure.toString().contains("SYNTHETIC_OPENAI_TEST_KEY"))
    }
    @Test fun http_401_is_authentication() = runTest { assertHttp(401, ModelProviderFailureKind.AUTHENTICATION) }
    @Test fun http_429_is_rate_limit() = runTest { assertHttp(429, ModelProviderFailureKind.RATE_LIMIT) }
    @Test fun http_503_is_server_failure() = runTest { assertHttp(503, ModelProviderFailureKind.SERVER) }
    private suspend fun assertHttp(code: Int, kind: ModelProviderFailureKind) {
        server.enqueue(MockResponse.Builder().code(code).body("SYNTHETIC_OPENAI_TEST_KEY").build())
        val failure = assertFailsWith<ModelProviderException> { provider().generate(request()) }
        assertEquals(kind, failure.kind); assertFalse(failure.toString().contains("SYNTHETIC_OPENAI_TEST_KEY"))
        assertEquals(1, server.requestCount)
    }
    @Test fun cancelled_stream_does_not_complete() = runTest {
        server.enqueue(MockResponse.Builder().headersDelay(30, TimeUnit.SECONDS).body(textStream("late")).build())
        val events = mutableListOf<ModelStreamEvent>()
        val job = CoroutineScope(Dispatchers.Default).launch { provider().stream(request()).collect { events += it } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(events.none { it is ModelStreamEvent.Completed })
    }
    @Test fun unknown_tool_or_disabled_tools_fail_before_registry() = runTest {
        enqueue(toolStream()); assertFailsWith<ModelProviderException> { provider().generate(request()) }
        enqueue(toolStream(name = "shell")); assertFailsWith<ModelProviderException> {
            provider(OpenAiConfiguration(structuredTools = true)).generate(request())
        }
    }
    @Test fun only_registered_function_schemas_are_exposed_no_native_tools() {
        val body = Json.parseToJsonElement(provider(OpenAiConfiguration(structuredTools = true)).requestBody(request())).jsonObject
        val schemas = body["tools"]!!.jsonArray
        assertEquals(listOf("echo", "protected_demo_tool"), schemas.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        schemas.forEach { assertEquals("function", it.jsonObject["type"]!!.jsonPrimitive.content); assertEquals("true", it.jsonObject["strict"].toString()) }
        listOf("computer_use", "code_interpreter", "mcp", "file_search", "web_search", "apply_patch", "async").forEach { assertFalse(schemas.toString().contains(it)) }
    }
    @Test fun continuation_replays_only_local_output_and_original_call_result_then_discards() = runTest {
        enqueue(toolStream(reasoning = true)); enqueue(textStream("Done"))
        val provider = provider(OpenAiConfiguration(structuredTools = true))
        val request = request()
        val proposal = provider.generate(request)
        val continuation = ModelContinuationRequest(request, proposal, ToolResult.Success("call-1", "echo", EchoOutput("OK")))
        assertEquals("Done", provider.streamContinuation(continuation).filterIsInstance<ModelStreamEvent.Completed>().single().response.content)
        server.takeRequest()
        val wire = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("none", wire["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("false", wire["store"].toString())
        assertFalse(wire.containsKey("previous_response_id")); assertFalse(wire.containsKey("tools"))
        assertTrue(wire["input"].toString().contains("encrypted-test-continuation"))
        assertEquals("call-1", wire["input"]!!.jsonArray.last().jsonObject["call_id"]!!.jsonPrimitive.content)
        assertFailsWith<ModelProviderException> { provider.streamContinuation(continuation).toList() }
    }
    @Test fun wrong_turn_or_changed_input_cannot_reuse_continuation() = runTest {
        enqueue(toolStream())
        val provider = provider(OpenAiConfiguration(structuredTools = true))
        val proposal = provider.generate(request())
        assertFailsWith<ModelProviderException> { provider.streamContinuation(ModelContinuationRequest(
            request().copy(turnId = "other"), proposal, ToolResult.Success("call-1", "echo", EchoOutput("OK")))).toList() }
        assertEquals(1, server.requestCount)
    }
    @Test fun finish_turn_discards_rejected_or_cancelled_continuation() = runTest {
        enqueue(toolStream())
        val provider = provider(OpenAiConfiguration(structuredTools = true))
        val proposal = provider.generate(request()); provider.finishTurn("t")
        assertFailsWith<ModelProviderException> { provider.streamContinuation(ModelContinuationRequest(
            request(), proposal, ToolResult.Success("call-1", "echo", EchoOutput("OK")))).toList() }
    }
    @Test fun reasoning_profiles_and_disabled_features_are_explicit() {
        listOf(ModelProfile.FAST to "low", ModelProfile.BALANCED to "medium", ModelProfile.DEEP to "high").forEach { (profile, effort) ->
            val provider = provider(OpenAiConfiguration(profile = profile))
            assertTrue(provider.requestBody(request()).contains("\"effort\":\"$effort\""))
            assertFalse(provider.capabilities().asyncTools); assertFalse(provider.capabilities().midTurnSteering)
            assertFalse(provider.capabilities().imageInput)
        }
        assertFailsWith<IllegalArgumentException> { OpenAiConfiguration(modelId = "unknown-astra-like").validated() }
    }
    @Test fun metrics_are_bounded_content_free_and_cost_includes_cache_writes() = runTest {
        val metrics = LocalProviderMetrics()
        enqueue(textStream("hi", usage = "\"usage\":{\"input_tokens\":1000,\"input_tokens_details\":{\"cached_tokens\":200,\"cache_write_tokens\":300},\"output_tokens\":100},"))
        provider(metrics = metrics).generate(request())
        val record = metrics.records.value.single()
        assertEquals(0.01395, record.approximateUsd!!, 0.0000001)
        assertNotNull(record.firstTokenMs)
        repeat(30) { metrics.record(record) }; assertEquals(20, metrics.records.value.size)
        assertFalse(record.toString().contains("Hello")); assertFalse(record.toString().contains("SYNTHETIC"))
        assertNull(approximateAstraCost(100, 0, null, 10)); assertNull(approximateAstraCost(100, 99, 99, 10))
    }
    @Test fun incomplete_response_never_completes() = runTest {
        enqueue(textStream("hi").replace("response.completed", "response.incomplete"))
        assertFailsWith<ModelProviderException> { provider().generate(request()) }
    }
    @Test fun hosted_output_items_are_rejected() = runTest {
        enqueue(toolStream().replace("\"function_call\"", "\"computer_call\""))
        assertFailsWith<ModelProviderException> { provider(OpenAiConfiguration(structuredTools = true)).generate(request()) }
    }
    @Test fun numeric_tool_arguments_are_not_coerced_to_text() = runTest {
        enqueue(toolStream(arguments = "{\"text\":123}"))
        assertFailsWith<ModelProviderException> { provider(OpenAiConfiguration(structuredTools = true)).generate(request()) }
    }

    @Test fun astra_proposals_cross_real_kernel_confirm_reject_and_ledger_boundary() = runTest {
        val fixture = runtimeFixture(provider(OpenAiConfiguration(structuredTools = true)))
        enqueue(toolStream("protected_demo_tool", "{\"action\":\"harmless test\"}"))
        val running = async { fixture.runtime.runTurn(AgentRequest("r", "s", "propose demo", Instant.EPOCH)) }
        val approval = fixture.approvals.pendingRequest.filterNotNull().first()
        assertEquals(RuntimePhase.WAITING_FOR_APPROVAL, fixture.runtime.state.value.phase)
        assertFalse(fixture.approvals.resolve("wrong-approval-id", dev.kinetic.core.policy.ApprovalDecision.APPROVE))
        assertTrue(fixture.approvals.resolve(approval.approvalId, dev.kinetic.core.policy.ApprovalDecision.REJECT))
        assertIs<AgentTurnResult.Cancelled>(running.await())
        assertEquals(1, server.requestCount)
        assertTrue(fixture.sessions.get("s")!!.journal.none {
            it.event is dev.kinetic.core.logging.JournalEvent.ToolExecutionStarted
        })
    }

    @Test fun astra_approved_tool_runs_once_and_duplicate_call_id_does_not_replay() = runTest {
        val fixture = runtimeFixture(provider(OpenAiConfiguration(structuredTools = true)))
        enqueue(toolStream("protected_demo_tool", "{\"action\":\"harmless test\"}")); enqueue(textStream("Completed"))
        val running = async { fixture.runtime.runTurn(AgentRequest("r", "s", "propose demo", Instant.EPOCH)) }
        val approval = fixture.approvals.pendingRequest.filterNotNull().first()
        assertTrue(fixture.approvals.resolve(approval.approvalId, dev.kinetic.core.policy.ApprovalDecision.APPROVE))
        assertIs<AgentTurnResult.Completed>(running.await())
        enqueue(toolStream("protected_demo_tool", "{\"action\":\"harmless test\"}"))
        assertIs<AgentTurnResult.Failed>(fixture.runtime.runTurn(AgentRequest("next", "s", "again", Instant.EPOCH)))
        assertEquals(1, fixture.sessions.get("s")!!.journal.count {
            it.event is dev.kinetic.core.logging.JournalEvent.ToolExecutionStarted
        })
    }

    @Test fun astra_context_preserves_planner_bounds_current_memory_and_derived_summary() {
        val now = Instant.EPOCH
        val current = dev.kinetic.core.memory.MemoryRecord(
            memoryId = "current", scope = dev.kinetic.core.memory.MemoryScope.USER,
            category = dev.kinetic.core.memory.MemoryCategory.PREFERENCE, content = "my preferred database is SQLite",
            provenance = dev.kinetic.core.memory.MemoryProvenance.USER_EXPLICIT, ownerSessionId = null, sourceSessionId = "s",
            sourceTurnId = "t", sourceMessageId = "m", createdAt = now, updatedAt = now,
        )
        val historical = current.copy(memoryId = "old", content = "my preferred database is PostgreSQL",
            deduplicationKey = dev.kinetic.core.memory.memoryDeduplicationKey(current.scope, current.category,
                "my preferred database is PostgreSQL", null),
            retentionState = dev.kinetic.core.memory.MemoryRetentionState.SUPERSEDED)
        val plan = dev.kinetic.core.context.ContextPlanner().plan(
            sessionId = "s", query = "preferred database", messages = request().messages,
            candidates = dev.kinetic.core.memory.MemoryContext(userMemories = listOf(current, historical)), summary = null,
        )
        assertTrue(plan.totalEstimatedTokens <= 8192)
        val wire = provider().requestBody(request().copy(contextPlan = plan, memoryContext = plan.memoryContext))
        assertTrue(wire.contains("SQLite")); assertFalse(wire.contains("PostgreSQL"))
        assertTrue(wire.contains("UNTRUSTED DATA")); assertTrue(wire.contains("derived historical context"))
    }

    private data class RuntimeFixture(
        val runtime: DefaultAgentRuntime,
        val approvals: dev.kinetic.core.policy.LedgerBackedApprovalGate,
        val sessions: dev.kinetic.core.session.InMemorySessionStore,
    )

    @Test fun summary_generation_uses_selected_transport_untrusted_source_and_no_tools() = runTest {
        enqueue(textStream("The user discussed a database choice; it is not approval."))
        val provider = provider(OpenAiConfiguration(structuredTools = true))
        val result = provider.generateSessionSummary(dev.kinetic.core.context.SummaryGenerationRequest(
            sessionId = "s", previousSummary = null, newMessages = request().messages,
        ))
        assertTrue(result.contains("not approval"))
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("none", body["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("false", body["store"].toString())
        assertFalse(body.containsKey("tools"))
        assertTrue(body["input"].toString().contains("KINETIC_SESSION_SUMMARY_SOURCE_UNTRUSTED"))
    }
    private fun runtimeFixture(provider: ModelProvider): RuntimeFixture {
        val clock = java.time.Clock.systemUTC()
        val ids = UuidIdGenerator()
        val sessions = dev.kinetic.core.session.InMemorySessionStore()
        val ledger = dev.kinetic.core.session.InMemoryRunLedger()
        val approvals = dev.kinetic.core.policy.LedgerBackedApprovalGate(ledger, clock)
        return RuntimeFixture(DefaultAgentRuntime(provider, ToolRegistry(DemoToolCatalog.create(clock)),
            dev.kinetic.core.policy.DefaultCapabilityPolicy(),
            dev.kinetic.core.policy.PolicyContext(dev.kinetic.core.policy.DistributionProfile.PLAY_CORE),
            approvals, sessions, ledger, dev.kinetic.core.logging.SessionEventJournal(sessions, ids, clock), clock, ids), approvals, sessions)
    }

    companion object {
        internal fun event(type: String, fields: String) = "event: $type\ndata: {\"type\":\"$type\",$fields}\n\n"
        internal fun textStream(text: String, usage: String = ""): String =
            event("response.created", "\"response\":{\"id\":\"resp-1\"}") +
                event("response.output_text.delta", "\"delta\":${JsonPrimitive(text)}") +
                event("response.completed", "\"response\":{\"id\":\"resp-1\",\"status\":\"completed\",$usage\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":${JsonPrimitive(text)}}]}]}")
        internal fun toolStream(name: String = "echo", arguments: String = "{\"text\":\"OK\"}", reasoning: Boolean = false): String {
            val item = "\"id\":\"item-1\",\"type\":\"function_call\",\"call_id\":\"call-1\",\"name\":\"$name\""
            val reasoningItem = if (reasoning) "{\"type\":\"reasoning\",\"id\":\"reason-1\",\"summary\":[],\"encrypted_content\":\"encrypted-test-continuation\"}," else ""
            return event("response.created", "\"response\":{\"id\":\"resp-1\"}") +
                event("response.output_item.added", "\"item\":{$item,\"arguments\":\"\"}") +
                event("response.function_call_arguments.delta", "\"item_id\":\"item-1\",\"delta\":${JsonPrimitive(arguments)}") +
                event("response.function_call_arguments.done", "\"item_id\":\"item-1\",\"arguments\":${JsonPrimitive(arguments)}") +
                event("response.completed", "\"response\":{\"id\":\"resp-1\",\"status\":\"completed\",\"output\":[$reasoningItem{$item,\"status\":\"completed\",\"arguments\":${JsonPrimitive(arguments)}}]}")
        }
    }
}
