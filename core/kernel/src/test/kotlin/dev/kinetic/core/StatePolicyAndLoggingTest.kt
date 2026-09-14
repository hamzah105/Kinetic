package dev.kinetic.core

import dev.kinetic.core.agent.AgentStateMachine
import dev.kinetic.core.agent.IllegalStateTransitionException
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.agent.RuntimeState
import dev.kinetic.core.logging.JournalSanitizer
import dev.kinetic.core.policy.DefaultCapabilityPolicy
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.PolicyContext
import dev.kinetic.core.policy.PolicyDecision
import dev.kinetic.core.tools.ProtectedDemoTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class StatePolicyAndLoggingTest {
    @Test
    fun `invalid state transition throws`() {
        val machine = AgentStateMachine()

        val failure = assertFailsWith<IllegalStateTransitionException> {
            machine.transition(RuntimeState.Completed("turn", "not allowed"))
        }

        assertEquals(RuntimePhase.IDLE, failure.from)
        assertEquals(RuntimePhase.COMPLETED, failure.to)
        assertEquals(RuntimePhase.IDLE, machine.state.value.phase)
    }

    @Test
    fun `confirmation metadata cannot be bypassed by provider output`() {
        val decision = DefaultCapabilityPolicy().evaluate(
            ProtectedDemoTool().definition,
            PolicyContext(DistributionProfile.PLAY_CORE),
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun `journal sanitizer redacts common credential forms`() {
        val redacted = JournalSanitizer.redact("token=abc123 password: hunter2 safe=value")

        assertFalse("abc123" in redacted)
        assertFalse("hunter2" in redacted)
        assertEquals("token=[REDACTED] password=[REDACTED] safe=value", redacted)
    }
}
