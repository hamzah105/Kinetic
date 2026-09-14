package dev.kinetic.core

import dev.kinetic.core.agent.AgentRequest
import dev.kinetic.core.agent.DefaultAgentRuntime
import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.context.InMemorySessionSummaryRepository
import dev.kinetic.core.logging.SessionEventJournal
import dev.kinetic.core.model.FakeModelProvider
import dev.kinetic.core.model.ModelProvider
import dev.kinetic.core.memory.InMemoryMemoryRepository
import dev.kinetic.core.policy.DefaultCapabilityPolicy
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.InteractiveApprovalGate
import dev.kinetic.core.policy.PolicyContext
import dev.kinetic.core.session.InMemorySessionStore
import dev.kinetic.core.session.InMemoryRunLedger
import dev.kinetic.core.tools.DemoToolCatalog
import dev.kinetic.core.tools.ToolRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

internal class SequenceIdGenerator : IdGenerator {
    private val sequence = AtomicLong(0)

    override fun nextId(prefix: String): String = "$prefix-${sequence.incrementAndGet()}"
}

internal class RuntimeTestFixture(
    provider: ModelProvider = FakeModelProvider(),
    val clock: Clock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC),
    val sessions: InMemorySessionStore = InMemorySessionStore(),
    val runLedger: InMemoryRunLedger = InMemoryRunLedger(),
    val approvalGate: InteractiveApprovalGate = InteractiveApprovalGate(),
    val memories: InMemoryMemoryRepository = InMemoryMemoryRepository(),
    val summaries: InMemorySessionSummaryRepository = InMemorySessionSummaryRepository(),
    policy: dev.kinetic.core.policy.CapabilityPolicy = DefaultCapabilityPolicy(),
) {
    val ids = SequenceIdGenerator()
    val journal = SessionEventJournal(sessions, ids, clock)
    val registry = ToolRegistry(DemoToolCatalog.create(clock))
    val runtime = DefaultAgentRuntime(
        modelProvider = provider,
        toolRegistry = registry,
        capabilityPolicy = policy,
        policyContext = PolicyContext(DistributionProfile.PLAY_CORE),
        approvalGate = approvalGate,
        sessionStore = sessions,
        runLedger = runLedger,
        journal = journal,
        clock = clock,
        idGenerator = ids,
        memoryRepository = memories,
        sessionSummaryRepository = summaries,
    )

    fun request(content: String, sessionId: String = "session-a") = AgentRequest(
        requestId = ids.nextId("request"),
        sessionId = sessionId,
        content = content,
        createdAt = clock.instant(),
    )
}
