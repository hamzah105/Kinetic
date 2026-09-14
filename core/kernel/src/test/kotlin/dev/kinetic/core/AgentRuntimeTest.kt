package dev.kinetic.core

import dev.kinetic.core.agent.AgentTurnResult
import dev.kinetic.core.agent.InvalidToolCall
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.agent.ModelFailure
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.agent.ToolFailure
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.model.FakeModelProvider
import dev.kinetic.core.tools.EchoOutput
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRuntimeTest {
    @Test
    fun `normal agent turn completes without a tool`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = fixture.runtime.runTurn(fixture.request("Hello kernel"))

        val completed = assertIs<AgentTurnResult.Completed>(result)
        assertNull(completed.toolResult)
        assertEquals(RuntimePhase.COMPLETED, fixture.runtime.state.value.phase)
    }

    @Test
    fun `fake model output is deterministic`() = runTest {
        val fixture = RuntimeTestFixture()

        val first = assertIs<AgentTurnResult.Completed>(
            fixture.runtime.runTurn(fixture.request("same prompt", "first")),
        )
        val second = assertIs<AgentTurnResult.Completed>(
            fixture.runtime.runTurn(fixture.request("same prompt", "second")),
        )

        assertEquals("Fake response: same prompt", first.modelResponse.content)
        assertEquals(first.modelResponse.content, second.modelResponse.content)
    }

    @Test
    fun `registered echo tool executes with typed output`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = assertIs<AgentTurnResult.Completed>(
            fixture.runtime.runTurn(fixture.request("echo: kinetic")),
        )
        val success = assertIs<ToolResult.Success>(result.toolResult)

        assertEquals("echo", success.toolId)
        assertEquals(EchoOutput("kinetic"), success.output)
    }

    @Test
    fun `unknown tool is rejected before execution`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = assertIs<AgentTurnResult.Failed>(
            fixture.runtime.runTurn(fixture.request("request an unknown tool")),
        )

        assertIs<InvalidToolCall>(result.error)
        assertEquals(RuntimePhase.FAILED, fixture.runtime.state.value.phase)
        assertTrue(fixture.journal.entries("session-a").none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    @Test
    fun `safe time tool runs without approval`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = assertIs<AgentTurnResult.Completed>(
            fixture.runtime.runTurn(fixture.request("current app time")),
        )

        assertIs<ToolResult.Success>(result.toolResult)
        assertNull(fixture.approvalGate.pendingRequest.value)
        assertTrue(fixture.journal.entries("session-a").none {
            it.event is JournalEvent.ApprovalRequested
        })
    }

    @Test
    fun `model failure becomes a typed safe error`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = assertIs<AgentTurnResult.Failed>(
            fixture.runtime.runTurn(fixture.request("trigger model failure")),
        )

        assertIs<ModelFailure>(result.error)
        assertEquals("The model could not complete the request.", result.error.userMessage)
    }

    @Test
    fun `tool execution failure becomes typed failure`() = runTest {
        val fixture = RuntimeTestFixture()

        val result = assertIs<AgentTurnResult.Failed>(
            fixture.runtime.runTurn(fixture.request("run failing demo")),
        )

        assertIs<ToolFailure>(result.error)
        assertEquals(RuntimePhase.FAILED, fixture.runtime.state.value.phase)
    }

    @Test
    fun `cancellation propagates and is journaled`() = runTest {
        val fixture = RuntimeTestFixture(FakeModelProvider(responseDelayMillis = 10_000))
        val turn = launch { fixture.runtime.runTurn(fixture.request("slow request")) }
        runCurrent()

        assertEquals(RuntimePhase.THINKING, fixture.runtime.state.value.phase)
        fixture.runtime.cancelActiveTurn()
        advanceTimeBy(1)
        turn.join()

        assertEquals(RuntimePhase.CANCELLED, fixture.runtime.state.value.phase)
        assertTrue(fixture.journal.entries("session-a").any {
            it.event is JournalEvent.CancellationRecorded
        })
    }

    @Test
    fun `sessions remain isolated`() = runTest {
        val fixture = RuntimeTestFixture()

        fixture.runtime.runTurn(fixture.request("alpha", "session-alpha"))
        fixture.runtime.runTurn(fixture.request("beta", "session-beta"))

        val alpha = fixture.sessions.get("session-alpha")!!
        val beta = fixture.sessions.get("session-beta")!!
        assertTrue(alpha.messages.all { "beta" !in it.content })
        assertTrue(beta.messages.all { "alpha" !in it.content })
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), alpha.messages.map { it.role })
    }

    @Test
    fun `journal entries have strict per-session ordering`() = runTest {
        val fixture = RuntimeTestFixture()

        fixture.runtime.runTurn(fixture.request("echo: ordered"))
        val entries = fixture.journal.entries("session-a")

        assertEquals((1L..entries.size.toLong()).toList(), entries.map { it.sequence })
        assertTrue(entries.zipWithNext().all { (left, right) -> left.sequence < right.sequence })
    }
}
