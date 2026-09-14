package dev.kinetic.core

import dev.kinetic.core.agent.AgentTurnResult
import dev.kinetic.core.agent.ModelFailure
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.agent.RuntimeRecovery
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.model.ModelProvider
import dev.kinetic.core.model.ModelProviderException
import dev.kinetic.core.model.ModelProviderFailureKind
import dev.kinetic.core.model.ModelRequest
import dev.kinetic.core.model.ModelResponse
import dev.kinetic.core.model.ModelStreamEvent
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.tools.NoToolInput
import dev.kinetic.core.tools.ToolCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingRuntimeTest {
    @Test
    fun `streaming deltas update only the active turn before durable completion`() = runTest {
        val release = CompletableDeferred<Unit>()
        val provider = object : ModelProvider {
            override val providerId = "stream-test"
            override suspend fun generate(request: ModelRequest) = error("stream expected")
            override fun stream(request: ModelRequest): Flow<ModelStreamEvent> = flow {
                emit(ModelStreamEvent.TextDelta("Kinetic "))
                release.await()
                emit(ModelStreamEvent.TextDelta("streams"))
                emit(ModelStreamEvent.Completed(ModelResponse("response-1", "Kinetic streams")))
            }
        }
        val fixture = RuntimeTestFixture(provider)
        val turn = async { fixture.runtime.runTurn(fixture.request("hello")) }
        runCurrent()

        assertEquals("Kinetic ", fixture.runtime.streamingOutput.value?.content)
        assertTrue(fixture.sessions.get("session-a")!!.messages.none {
            it.role == dev.kinetic.core.agent.MessageRole.ASSISTANT
        })

        release.complete(Unit)
        assertIs<AgentTurnResult.Completed>(turn.await())
        assertEquals("Kinetic streams", fixture.runtime.streamingOutput.value?.content)
        assertEquals("Kinetic streams", fixture.sessions.get("session-a")!!.messages.last().content)
    }

    @Test
    fun `cancellation stops later stream deltas and records cancellation`() = runTest {
        val provider = object : ModelProvider {
            override val providerId = "cancel-stream"
            override suspend fun generate(request: ModelRequest) = error("stream expected")
            override fun stream(request: ModelRequest): Flow<ModelStreamEvent> = flow {
                emit(ModelStreamEvent.TextDelta("first"))
                delay(10_000)
                emit(ModelStreamEvent.TextDelta("must-not-arrive"))
                emit(ModelStreamEvent.Completed(ModelResponse("late", "firstmust-not-arrive")))
            }
        }
        val fixture = RuntimeTestFixture(provider)
        val turn = async { fixture.runtime.runTurn(fixture.request("cancel me")) }
        runCurrent()
        assertEquals("first", fixture.runtime.streamingOutput.value?.content)

        fixture.runtime.cancelActiveTurn()
        advanceUntilIdle()
        assertTrue(turn.isCancelled)
        assertEquals(RuntimePhase.CANCELLED, fixture.runtime.state.value.phase)
        assertNull(fixture.runtime.streamingOutput.value)
        assertTrue(fixture.sessions.get("session-a")!!.messages.none {
            it.content.contains("must-not-arrive")
        })
    }

    @Test
    fun `conversation-only provider tool payload fails before registry execution`() = runTest {
        val provider = object : ModelProvider {
            override val providerId = "conversation-only"
            override suspend fun generate(request: ModelRequest) = ModelResponse(
                responseId = "unsafe",
                content = "attempted tool call",
                toolCalls = listOf(ToolCall("call-unsafe", "current_app_time", NoToolInput)),
            )
        }
        val fixture = RuntimeTestFixture(provider)

        val result = assertIs<AgentTurnResult.Failed>(
            fixture.runtime.runTurn(fixture.request("try a tool")),
        )

        assertEquals("model_malformed_response", result.error.code)
        assertTrue(fixture.journal.entries("session-a").none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    @Test
    fun `typed provider configuration failure reaches safe agent error`() = runTest {
        val provider = object : ModelProvider {
            override val providerId = "cloud"
            override suspend fun generate(request: ModelRequest): ModelResponse {
                throw ModelProviderException(
                    ModelProviderFailureKind.CONFIGURATION,
                    "Configure an API key before using cloud mode.",
                )
            }
        }
        val fixture = RuntimeTestFixture(provider)

        val failed = assertIs<AgentTurnResult.Failed>(
            fixture.runtime.runTurn(fixture.request("hello cloud")),
        )

        val error = assertIs<ModelFailure>(failed.error)
        assertEquals("model_configuration_missing", error.code)
        assertEquals("Configure an API key before using cloud mode.", error.userMessage)
    }

    @Test
    fun `recovery never invokes provider for interrupted network turn`() = runTest {
        var calls = 0
        val provider = object : ModelProvider {
            override val providerId = "counting-cloud"
            override suspend fun generate(request: ModelRequest): ModelResponse {
                calls += 1
                return ModelResponse("unexpected", "unexpected")
            }
        }
        val fixture = RuntimeTestFixture(provider)
        fixture.sessions.getOrCreate("cloud-session", fixture.clock.instant())
        fixture.runLedger.beginTurn(
            DurableTurnRecord(
                turnId = "cloud-turn",
                sessionId = "cloud-session",
                requestId = "cloud-request",
                requestSummary = "interrupted",
                status = DurableTurnStatus.THINKING,
                createdAt = fixture.clock.instant(),
                updatedAt = fixture.clock.instant(),
            ),
        )

        assertIs<RuntimeRecovery.Restored>(fixture.runtime.recover("cloud-session"))
        assertEquals(0, calls)
        assertEquals(RuntimePhase.FAILED, fixture.runtime.state.value.phase)
    }
}
