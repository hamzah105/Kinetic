package dev.kinetic.core

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.context.ContextBudget
import dev.kinetic.core.context.ContextPlanner
import dev.kinetic.core.context.DeterministicMemoryRanker
import dev.kinetic.core.context.SessionSummary
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryContext
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextPlanningTest {
    @Test
    fun `older relevant USER memory outranks newer irrelevant memories`() {
        val relevant = memory(
            id = "postgres",
            content = "my preferred Kinetic test database is PostgreSQL",
            at = Instant.parse("2026-08-01T00:00:00Z"),
        )
        val irrelevant = (1..5).map { index ->
            memory(
                id = "new-$index",
                content = "my unrelated editor color number $index is blue",
                at = Instant.parse("2026-08-2${index}T00:00:00Z"),
            )
        }

        val ranked = DeterministicMemoryRanker().rank(
            "What is my preferred Kinetic test database?",
            irrelevant + relevant,
            4,
        )

        assertEquals(listOf("postgres"), ranked.map { it.record.memoryId })
        assertTrue(ranked.single().score > 0)
    }

    @Test
    fun `planner retrieves replacement only and historical summary cannot reactivate superseded value`() {
        val postgres = memory(
            id = "postgres-superseded",
            content = "my preferred Kinetic test database is PostgreSQL",
            at = Instant.parse("2026-08-01T00:00:00Z"),
            retentionState = MemoryRetentionState.SUPERSEDED,
        )
        val sqlite = memory(
            id = "sqlite-active",
            content = "my preferred Kinetic test database is SQLite",
            at = Instant.parse("2026-08-02T00:00:00Z"),
        )
        val historicalSummary = summary(
            content = "Earlier history mentioned preferred Kinetic test database PostgreSQL",
            lastCovered = 1,
        )

        val plan = ContextPlanner().plan(
            sessionId = "session-a",
            query = "What is my preferred Kinetic test database?",
            candidates = MemoryContext(userMemories = listOf(postgres, sqlite)),
            summary = historicalSummary,
            messages = listOf(message(2, MessageRole.USER, "What is my preferred Kinetic test database?")),
        )

        assertEquals(listOf("sqlite-active"), plan.memoryContext.userMemories.map { it.memoryId })
        assertTrue(plan.memoryDiagnostics.none { it.memoryId == "postgres-superseded" })
        assertEquals(historicalSummary, plan.summary)
    }

    @Test
    fun `zero relevance does not fill memory quota by recency`() {
        val ranked = DeterministicMemoryRanker().rank(
            "Which database should I use?",
            listOf(memory("recent", "favorite editor is VS Code")),
            4,
        )

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `ranking is deterministic by score then recency then id`() {
        val old = memory("old", "database PostgreSQL", Instant.parse("2026-08-01T00:00:00Z"))
        val newerB = memory("b", "database PostgreSQL", Instant.parse("2026-08-02T00:00:00Z"))
        val newerA = memory("a", "database PostgreSQL", Instant.parse("2026-08-02T00:00:00Z"))

        val ids = DeterministicMemoryRanker().rank("database", listOf(old, newerB, newerA), 4)
            .map { it.record.memoryId }

        assertEquals(listOf("a", "b", "old"), ids)
    }

    @Test
    fun `planner caps USER and SESSION memories independently`() {
        val user = (1..7).map { memory("u-$it", "database PostgreSQL item $it") }
        val session = (1..7).map {
            memory("s-$it", "database PostgreSQL session item $it", scope = MemoryScope.SESSION)
        }

        val plan = ContextPlanner().plan(
            sessionId = "session-a",
            query = "database PostgreSQL",
            candidates = MemoryContext(user, session),
            summary = null,
            messages = listOf(message(1, MessageRole.USER, "database PostgreSQL")),
        )

        assertEquals(4, plan.memoryContext.userMemories.size)
        assertEquals(4, plan.memoryContext.sessionMemories.size)
        assertEquals(14, plan.memoriesConsidered)
    }

    @Test
    fun `summary covered messages are omitted and recent uncovered range is ordered`() {
        val messages = (1L..8L).map { sequence ->
            message(
                sequence,
                if (sequence % 2L == 1L) MessageRole.USER else MessageRole.ASSISTANT,
                "message $sequence",
            )
        }
        val summary = summary(lastCovered = 4)

        val plan = ContextPlanner().plan(
            sessionId = "session-a",
            query = "message eight",
            candidates = MemoryContext(),
            summary = summary,
            messages = messages,
        )

        assertEquals(summary, plan.summary)
        assertEquals(4, plan.omittedCoveredMessages)
        assertEquals(listOf(5L, 6L, 7L, 8L), plan.selectedMessages.map { it.sequence })
        assertEquals(5L, plan.selectedMessageFirstSequence)
        assertEquals(8L, plan.selectedMessageLastSequence)
        assertTrue(plan.selectedMessages.none { it.sequence <= summary.lastCoveredMessageSequence })
    }

    @Test
    fun `current USER request survives a tight budget while oversized artifacts are omitted`() {
        val planner = ContextPlanner(
            ContextBudget(
                totalEstimatedTokens = 220,
                reservedOutputTokens = 80,
                reservedSystemTokens = 80,
                maxRecentMessages = 3,
            ),
        )
        val current = message(3, MessageRole.USER, "database")
        val plan = planner.plan(
            sessionId = "session-a",
            query = "database",
            candidates = MemoryContext(
                userMemories = listOf(memory("huge", "database ${"x".repeat(800)}")),
            ),
            summary = summary(content = "s".repeat(300), lastCovered = 1),
            messages = listOf(
                message(1, MessageRole.USER, "old"),
                message(2, MessageRole.ASSISTANT, "y".repeat(400)),
                current,
            ),
        )

        assertEquals(listOf(current.messageId), plan.selectedMessages.map { it.messageId })
        assertTrue(plan.memoryContext.ordered.isEmpty())
        assertNull(plan.summary)
        assertTrue("memory_budget" in plan.omissionReasons)
        assertTrue("summary_budget" in plan.omissionReasons)
        assertTrue(plan.totalEstimatedTokens <= plan.budgetLimitTokens)
    }

    @Test
    fun `long irrelevant memories cannot dominate a short relevant memory`() {
        val relevant = memory("relevant", "preferred database PostgreSQL")
        val noise = memory("noise", "editor ${"color ".repeat(100)}")

        val ranked = DeterministicMemoryRanker().rank("preferred database", listOf(noise, relevant), 4)

        assertEquals("relevant", ranked.single().record.memoryId)
    }

    @Test
    fun `planner reports zero relevance without exposing memory content`() {
        val plan = ContextPlanner().plan(
            sessionId = "session-a",
            query = "database",
            candidates = MemoryContext(userMemories = listOf(memory("private-id", "favorite editor"))),
            summary = null,
            messages = listOf(message(1, MessageRole.USER, "database")),
        )

        assertTrue(plan.memoryDiagnostics.isEmpty())
        assertEquals(listOf("zero_relevance"), plan.omissionReasons)
        assertFalse(plan.toString().contains("favorite editor"))
    }

    @Test
    fun `extremely long conversation remains bounded deterministic and keeps current request`() {
        val messages = (1L..250L).map { sequence ->
            message(
                sequence,
                if (sequence % 2L == 1L) MessageRole.USER else MessageRole.ASSISTANT,
                "conversation message $sequence ${"x".repeat(80)}",
            )
        }
        val planner = ContextPlanner()

        val first = planner.plan("session-a", "current request", MemoryContext(), null, messages)
        val second = planner.plan("session-a", "current request", MemoryContext(), null, messages)

        assertEquals(first, second)
        assertTrue(first.selectedMessages.size <= 20)
        assertTrue(first.selectedMessages.any { it.sequence == 249L })
        assertTrue(first.totalEstimatedTokens <= first.budgetLimitTokens)
        assertTrue(first.omittedBudgetMessages > 0)
    }

    private fun message(sequence: Long, role: MessageRole, content: String) = AgentMessage(
        messageId = "message-$sequence",
        turnId = "turn-$sequence",
        role = role,
        content = content,
        createdAt = Instant.EPOCH.plusSeconds(sequence),
        sequence = sequence,
    )

    private fun memory(
        id: String,
        content: String,
        at: Instant = Instant.parse("2026-08-25T00:00:00Z"),
        scope: MemoryScope = MemoryScope.USER,
        retentionState: MemoryRetentionState = MemoryRetentionState.ACTIVE,
    ) = MemoryRecord(
        memoryId = id,
        scope = scope,
        category = if (scope == MemoryScope.USER) MemoryCategory.PREFERENCE else MemoryCategory.TASK_CONTEXT,
        content = content,
        provenance = MemoryProvenance.USER_EXPLICIT,
        ownerSessionId = "session-a".takeIf { scope == MemoryScope.SESSION },
        sourceSessionId = "session-a",
        sourceTurnId = "source-turn-$id",
        sourceMessageId = "source-message-$id",
        createdAt = at,
        updatedAt = at,
        retentionState = retentionState,
    )

    private fun summary(
        content: String = "Earlier session context",
        lastCovered: Long,
    ) = SessionSummary(
        summaryId = "summary-$lastCovered",
        sessionId = "session-a",
        content = content,
        firstCoveredMessageSequence = 1,
        lastCoveredMessageSequence = lastCovered,
        sourceMessageCount = lastCovered.toInt(),
        createdAt = Instant.EPOCH,
        sourceDigest = "a".repeat(64),
    )
}
