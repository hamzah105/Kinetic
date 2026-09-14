package dev.kinetic.app

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.ApprovalRejected
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.agent.RuntimeState
import dev.kinetic.core.agent.StreamingModelOutput
import dev.kinetic.core.session.AgentSession
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.memory.RememberResult
import dev.kinetic.core.tools.EchoOutput
import dev.kinetic.core.tools.ToolResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelUiStateTest {
    private val now = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun `rejected turn never displays tool output from a prior approved turn`() {
        val session = sessionWithPriorSuccessfulTurn()

        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Cancelled("turn-new", ApprovalRejected("approval-new")),
            approval = null,
            session = session,
            isRecovering = false,
        )

        assertNull(ui.toolOutput)
        assertNull(ui.modelOutput)
    }

    @Test
    fun `new thinking turn does not inherit prior model or tool output`() {
        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Thinking("turn-new"),
            approval = null,
            session = sessionWithPriorSuccessfulTurn(),
            isRecovering = false,
        )

        assertNull(ui.modelOutput)
        assertNull(ui.toolOutput)
    }

    @Test
    fun `completed state displays only its own current tool result`() {
        val currentResult = ToolResult.Success(
            callId = "call-new",
            toolId = "echo",
            output = EchoOutput("current output"),
        )
        val session = sessionWithPriorSuccessfulTurn().copy(
            messages = sessionWithPriorSuccessfulTurn().messages + AgentMessage(
                messageId = "assistant-new",
                turnId = "turn-new",
                role = MessageRole.ASSISTANT,
                content = "current model response",
                createdAt = now,
            ),
        )

        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Completed("turn-new", "current output", currentResult),
            approval = null,
            session = session,
            isRecovering = false,
        )

        assertEquals("current model response", ui.modelOutput)
        assertEquals("current output", ui.toolOutput)
    }

    @Test
    fun `assistant streaming output is scoped to the active turn`() {
        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Thinking("turn-new"),
            approval = null,
            session = sessionWithPriorSuccessfulTurn(),
            isRecovering = false,
            streamingOutput = StreamingModelOutput("turn-new", "partial cloud answer"),
        )

        assertEquals("partial cloud answer", ui.streamingText)
        assertNull(ui.toolOutput)
    }

    @Test
    fun `stale streaming delta from another turn is hidden`() {
        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Thinking("turn-new"),
            approval = null,
            session = sessionWithPriorSuccessfulTurn(),
            isRecovering = false,
            streamingOutput = StreamingModelOutput("turn-old", "stale partial"),
        )

        assertNull(ui.streamingText)
    }

    @Test
    fun `memory lists and safe status are exposed without changing conversation history`() {
        val userMemory = memory("user-memory", MemoryScope.USER)
        val sessionMemory = memory("session-memory", MemoryScope.SESSION)
        val session = sessionWithPriorSuccessfulTurn()

        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Idle,
            approval = null,
            session = session,
            isRecovering = false,
            userMemories = listOf(userMemory),
            sessionMemories = listOf(sessionMemory),
            memoryStatus = "Memory saved.",
        )

        assertEquals(listOf(userMemory), ui.userMemories)
        assertEquals(listOf(sessionMemory), ui.sessionMemories)
        assertEquals("Memory saved.", ui.memoryStatus)
        assertEquals(session.messages, ui.messages)
    }

    @Test
    fun `memory operation disables concurrent conversation work`() {
        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Idle,
            approval = null,
            session = sessionWithPriorSuccessfulTurn(),
            isRecovering = false,
            memoryOperationActive = true,
        )

        assertTrue(ui.isTurnActive)
    }

    @Test
    fun `summary operation disables concurrent conversation work and exposes safe status`() {
        val ui = reduceKernelUiState(
            runtimeState = RuntimeState.Idle,
            approval = null,
            session = sessionWithPriorSuccessfulTurn(),
            isRecovering = false,
            summaryOperationActive = true,
            summaryStatus = "Compacting older contextâ€¦",
        )

        assertTrue(ui.isTurnActive)
        assertEquals("Compacting older contextâ€¦", ui.summaryStatus)
    }

    @Test
    fun `rejected memory request never exposes secret in durable history text`() {
        val secret = "sk-do-not-persist-123456789"
        val safe = safeRememberRequestForHistory(
            "Remember that API key: $secret",
            RememberResult.Rejected(
                "memory_sensitive_content",
                "Kinetic did not store credential data.",
            ),
        )

        assertEquals("Remember request rejected: sensitive or invalid content omitted.", safe)
        assertTrue(secret !in safe)
    }

    private fun sessionWithPriorSuccessfulTurn() = AgentSession(
        sessionId = "developer-session",
        createdAt = now,
        updatedAt = now,
        messages = listOf(
            AgentMessage(
                messageId = "assistant-old",
                turnId = "turn-old",
                role = MessageRole.ASSISTANT,
                content = "old model response",
                createdAt = now,
            ),
            AgentMessage(
                messageId = "tool-old",
                turnId = "turn-old",
                role = MessageRole.TOOL,
                content = "stale successful tool output",
                createdAt = now,
            ),
        ),
    )

    private fun memory(content: String, scope: MemoryScope) = MemoryRecord(
        memoryId = "memory-$content",
        scope = scope,
        category = if (scope == MemoryScope.USER) MemoryCategory.PREFERENCE else MemoryCategory.TASK_CONTEXT,
        content = content,
        provenance = MemoryProvenance.USER_EXPLICIT,
        ownerSessionId = "developer-session".takeIf { scope == MemoryScope.SESSION },
        sourceSessionId = "developer-session",
        sourceTurnId = "memory-turn",
        sourceMessageId = "memory-message",
        createdAt = now,
        updatedAt = now,
    )
}
