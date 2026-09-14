package dev.kinetic.core

import dev.kinetic.core.agent.ApprovalRejected
import dev.kinetic.core.agent.RecoveryInterrupted
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.agent.RuntimeRecovery
import dev.kinetic.core.agent.RuntimeState
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.session.DurableApprovalStatus
import dev.kinetic.core.session.DurableEffectStatus
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.session.InMemoryRunLedger
import dev.kinetic.core.session.InMemorySessionStore
import dev.kinetic.core.tools.ProtectedDemoInput
import dev.kinetic.core.tools.RecoveredToolOutput
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DurabilityRecoveryTest {
    @Test
    fun `completed effect restores exact terminal output without replay`() = runTest {
        val first = RuntimeTestFixture()
        first.runtime.runTurn(first.request("echo: durable"))
        val startsBeforeRecovery = first.journal.entries("session-a").count {
            it.event is JournalEvent.ToolExecutionStarted
        }

        val restarted = RuntimeTestFixture(
            sessions = first.sessions,
            runLedger = first.runLedger,
        )
        val recovery = assertIs<RuntimeRecovery.Restored>(restarted.runtime.recover("session-a"))
        val completed = assertIs<RuntimeState.Completed>(recovery.state)
        val result = assertIs<ToolResult.Success>(completed.toolResult)

        assertEquals("durable", assertIs<RecoveredToolOutput>(result.output).text)
        assertEquals(startsBeforeRecovery, restarted.journal.entries("session-a").count {
            it.event is JournalEvent.ToolExecutionStarted
        })
        assertEquals(DurableEffectStatus.COMPLETED, restarted.runLedger.latestRun("session-a")!!.effect!!.status)
    }

    @Test
    fun `pending approval restores pending and requires the original explicit decision`() = runTest {
        val sessions = InMemorySessionStore()
        val ledger = InMemoryRunLedger()
        val seed = RuntimeTestFixture(sessions = sessions, runLedger = ledger)
        val approval = seedPendingApproval(seed)
        val restarted = RuntimeTestFixture(sessions = sessions, runLedger = ledger)

        val recovery = assertIs<RuntimeRecovery.PendingApproval>(
            restarted.runtime.recover(approval.sessionId),
        )
        assertEquals(RuntimePhase.WAITING_FOR_APPROVAL, restarted.runtime.state.value.phase)
        val resumed = async { restarted.runtime.resumePendingApproval(recovery) }
        runCurrent()

        assertEquals(approval, restarted.approvalGate.pendingRequest.value)
        assertFalse(restarted.approvalGate.resolve("wrong-approval", ApprovalDecision.APPROVE))
        assertTrue(restarted.journal.entries(approval.sessionId).none {
            it.event is JournalEvent.ToolExecutionStarted
        })

        assertTrue(restarted.approvalGate.resolve(approval.approvalId, ApprovalDecision.REJECT))
        val cancelled = assertIs<dev.kinetic.core.agent.AgentTurnResult.Cancelled>(resumed.await())
        assertIs<ApprovalRejected>(cancelled.error)
        assertEquals(DurableTurnStatus.CANCELLED, ledger.latestRun(approval.sessionId)!!.turn.status)
        assertTrue(restarted.journal.entries(approval.sessionId).none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    @Test
    fun `durable approval is bound to its original turn and call`() = runTest {
        val fixture = RuntimeTestFixture()
        val approval = seedPendingApproval(fixture)

        assertFalse(
            fixture.runLedger.resolveApproval(
                approval.approvalId,
                "different-turn",
                approval.toolCall.callId,
                ApprovalDecision.APPROVE,
                fixture.clock.instant(),
            ),
        )
        assertFalse(
            fixture.runLedger.resolveApproval(
                approval.approvalId,
                approval.turnId,
                "different-call",
                ApprovalDecision.APPROVE,
                fixture.clock.instant(),
            ),
        )
        assertEquals(
            DurableApprovalStatus.PENDING,
            fixture.runLedger.latestRun(approval.sessionId)!!.approval!!.status,
        )
    }

    @Test
    fun `rejected decision surviving a crash recovers cancelled without executing`() = runTest {
        val fixture = RuntimeTestFixture()
        val approval = seedPendingApproval(fixture)
        assertTrue(
            fixture.runLedger.resolveApproval(
                approval.approvalId,
                approval.turnId,
                approval.toolCall.callId,
                ApprovalDecision.REJECT,
                fixture.clock.instant(),
            ),
        )
        val restarted = RuntimeTestFixture(
            sessions = fixture.sessions,
            runLedger = fixture.runLedger,
        )

        val recovery = assertIs<RuntimeRecovery.Restored>(restarted.runtime.recover(approval.sessionId))

        assertIs<RuntimeState.Cancelled>(recovery.state)
        assertEquals(DurableTurnStatus.CANCELLED, fixture.runLedger.latestRun(approval.sessionId)!!.turn.status)
        assertTrue(restarted.journal.entries(approval.sessionId).none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    @Test
    fun `approved decision interrupted before execution fails closed`() = runTest {
        val fixture = RuntimeTestFixture()
        val approval = seedPendingApproval(fixture)
        assertTrue(
            fixture.runLedger.resolveApproval(
                approval.approvalId,
                approval.turnId,
                approval.toolCall.callId,
                ApprovalDecision.APPROVE,
                fixture.clock.instant(),
            ),
        )
        val restarted = RuntimeTestFixture(
            sessions = fixture.sessions,
            runLedger = fixture.runLedger,
        )

        val recovery = assertIs<RuntimeRecovery.Restored>(restarted.runtime.recover(approval.sessionId))

        assertIs<RecoveryInterrupted>(assertIs<RuntimeState.Failed>(recovery.state).error)
        assertEquals(DurableTurnStatus.FAILED, fixture.runLedger.latestRun(approval.sessionId)!!.turn.status)
        assertTrue(restarted.journal.entries(approval.sessionId).none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    @Test
    fun `failed and cancelled terminal states restore accurately`() = runTest {
        val failed = RuntimeTestFixture()
        failed.runtime.runTurn(failed.request("trigger model failure", "failed-session"))
        val failedRestart = RuntimeTestFixture(
            sessions = failed.sessions,
            runLedger = failed.runLedger,
        )
        val failedRecovery = assertIs<RuntimeRecovery.Restored>(
            failedRestart.runtime.recover("failed-session"),
        )
        assertEquals(
            "model_failure",
            assertIs<RuntimeState.Failed>(failedRecovery.state).error.code,
        )

        val cancelled = RuntimeTestFixture()
        val approval = seedPendingApproval(cancelled)
        cancelled.runLedger.cancelTurn(
            approval.turnId,
            ApprovalRejected(approval.approvalId),
            cancelled.clock.instant(),
        )
        val cancelledRestart = RuntimeTestFixture(
            sessions = cancelled.sessions,
            runLedger = cancelled.runLedger,
        )
        val cancelledRecovery = assertIs<RuntimeRecovery.Restored>(
            cancelledRestart.runtime.recover(approval.sessionId),
        )
        assertIs<ApprovalRejected>(assertIs<RuntimeState.Cancelled>(cancelledRecovery.state).error)
    }

    @Test
    fun `interrupted thinking and executing turns fail closed`() = runTest {
        val thinking = RuntimeTestFixture()
        seedTurn(thinking, "thinking-session", DurableTurnStatus.THINKING)
        val thinkingRecovery = assertIs<RuntimeRecovery.Restored>(
            thinking.runtime.recover("thinking-session"),
        )
        assertIs<RecoveryInterrupted>(assertIs<RuntimeState.Failed>(thinkingRecovery.state).error)

        val executing = RuntimeTestFixture()
        val turn = seedTurn(executing, "executing-session", DurableTurnStatus.THINKING)
        executing.runLedger.prepareEffect(
            turn.sessionId,
            turn.turnId,
            "call-executing",
            "echo",
            executing.clock.instant(),
        )
        assertTrue(
            executing.runLedger.markExecuting(
                turn.turnId,
                "call-executing",
                executing.clock.instant(),
            ),
        )
        val executingRecovery = assertIs<RuntimeRecovery.Restored>(
            executing.runtime.recover("executing-session"),
        )

        assertIs<RecoveryInterrupted>(assertIs<RuntimeState.Failed>(executingRecovery.state).error)
        assertEquals(DurableTurnStatus.FAILED, executing.runLedger.latestRun("executing-session")!!.turn.status)
        assertEquals(DurableEffectStatus.FAILED, executing.runLedger.latestRun("executing-session")!!.effect!!.status)
        assertTrue(executing.journal.entries("executing-session").none {
            it.event is JournalEvent.ToolExecutionStarted
        })
    }

    private suspend fun seedPendingApproval(fixture: RuntimeTestFixture): ApprovalRequest {
        val turn = seedTurn(fixture, "recovery-session", DurableTurnStatus.THINKING)
        val toolCall = ToolCall(
            callId = "call-protected",
            toolId = "protected_demo_tool",
            input = ProtectedDemoInput("run"),
        )
        fixture.runLedger.prepareEffect(
            turn.sessionId,
            turn.turnId,
            toolCall.callId,
            toolCall.toolId,
            fixture.clock.instant(),
        )
        val capability = assertNotNull(
            fixture.registry.definitions().singleOrNull { it.id == toolCall.toolId },
        ).capability
        return ApprovalRequest(
            approvalId = "approval-protected",
            sessionId = turn.sessionId,
            turnId = turn.turnId,
            toolCall = toolCall,
            capability = capability,
            requestedAt = fixture.clock.instant(),
        ).also { fixture.runLedger.requestApproval(it) }
    }

    private suspend fun seedTurn(
        fixture: RuntimeTestFixture,
        sessionId: String,
        status: DurableTurnStatus,
    ): DurableTurnRecord {
        fixture.sessions.getOrCreate(sessionId, fixture.clock.instant())
        return DurableTurnRecord(
            turnId = "turn-$sessionId",
            sessionId = sessionId,
            requestId = "request-$sessionId",
            requestSummary = "durability test",
            status = status,
            createdAt = fixture.clock.instant(),
            updatedAt = fixture.clock.instant(),
        ).also { fixture.runLedger.beginTurn(it) }
    }
}
