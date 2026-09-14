package dev.kinetic.core

import dev.kinetic.core.agent.AgentTurnResult
import dev.kinetic.core.agent.ApprovalRejected
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.tools.ProtectedDemoOutput
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalAcceptanceTest {
    @Test
    fun `confirmation tool reaches waiting state before execution`() = runTest {
        val fixture = RuntimeTestFixture()
        val turn = async { fixture.runtime.runTurn(fixture.request("Run the protected demo action")) }
        runCurrent()

        assertEquals(RuntimePhase.WAITING_FOR_APPROVAL, fixture.runtime.state.value.phase)
        assertNotNull(fixture.approvalGate.pendingRequest.value)
        assertTrue(fixture.journal.entries("session-a").none {
            it.event is JournalEvent.ToolExecutionStarted
        })

        fixture.approvalGate.resolve(
            fixture.approvalGate.pendingRequest.value!!.approvalId,
            ApprovalDecision.REJECT,
        )
        turn.await()
    }

    @Test
    fun `approved protected demo executes and completes with accurate journal`() = runTest {
        val fixture = RuntimeTestFixture()
        val turn = async { fixture.runtime.runTurn(fixture.request("Run the protected demo action")) }
        runCurrent()
        val approval = fixture.approvalGate.pendingRequest.value!!

        assertTrue(fixture.approvalGate.resolve(approval.approvalId, ApprovalDecision.APPROVE))
        val result = assertIs<AgentTurnResult.Completed>(turn.await())
        val success = assertIs<ToolResult.Success>(result.toolResult)

        assertEquals(ProtectedDemoOutput("Protected demo action completed."), success.output)
        assertEquals(RuntimePhase.COMPLETED, fixture.runtime.state.value.phase)
        assertEquals(
            listOf(
                RuntimePhase.IDLE to RuntimePhase.THINKING,
                RuntimePhase.THINKING to RuntimePhase.WAITING_FOR_APPROVAL,
                RuntimePhase.WAITING_FOR_APPROVAL to RuntimePhase.EXECUTING,
                RuntimePhase.EXECUTING to RuntimePhase.COMPLETED,
            ),
            fixture.journal.entries("session-a")
                .mapNotNull { it.event as? JournalEvent.StateTransition }
                .map { it.from to it.to },
        )
        assertTrue(fixture.journal.entries("session-a").any {
            it.event is JournalEvent.ApprovalAccepted
        })
    }

    @Test
    fun `rejected protected demo cancels without execution and journals rejection`() = runTest {
        val fixture = RuntimeTestFixture()
        val turn = async { fixture.runtime.runTurn(fixture.request("Run the protected demo action")) }
        runCurrent()
        val approval = fixture.approvalGate.pendingRequest.value!!

        assertTrue(fixture.approvalGate.resolve(approval.approvalId, ApprovalDecision.REJECT))
        val result = assertIs<AgentTurnResult.Cancelled>(turn.await())

        assertIs<ApprovalRejected>(result.error)
        assertEquals(RuntimePhase.CANCELLED, fixture.runtime.state.value.phase)
        val events = fixture.journal.entries("session-a").map { it.event }
        assertTrue(events.any { it is JournalEvent.ApprovalRejected })
        assertTrue(events.none { it is JournalEvent.ToolExecutionStarted })
    }
}
