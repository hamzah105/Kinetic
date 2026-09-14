package dev.kinetic.app.ui

import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.memory.memoryGovernanceKey
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryUiPresentationTest {
    @Test
    fun `history groups lifecycle versions and exposes only truthful actions`() {
        val active = memory("active", "SQLite", MemoryRetentionState.ACTIVE, 3)
        val superseded = memory("old", "PostgreSQL", MemoryRetentionState.SUPERSEDED, 1)
        val conflicted = memory("conflict", "Room", MemoryRetentionState.CONFLICTED, 2)

        val groups = groupMemoryHistory(listOf(superseded, active, conflicted))

        assertEquals(listOf("active", "conflict", "old"), groups.single().map { it.memoryId })
        assertEquals(
            setOf(MemoryUiAction.EDIT_REPLACE, MemoryUiAction.DELETE),
            memoryUiActions(active),
        )
        assertEquals(
            setOf(MemoryUiAction.RESOLVE, MemoryUiAction.DELETE),
            memoryUiActions(conflicted),
        )
        assertEquals(setOf(MemoryUiAction.DELETE), memoryUiActions(superseded))
    }

    private fun memory(
        id: String,
        value: String,
        state: MemoryRetentionState,
        second: Long,
    ): MemoryRecord {
        val subject = "preferred Kinetic test database"
        val at = Instant.parse("2026-09-02T00:00:00Z").plusSeconds(second)
        return MemoryRecord(
            memoryId = id,
            scope = MemoryScope.USER,
            category = MemoryCategory.PREFERENCE,
            content = "my $subject is $value",
            provenance = MemoryProvenance.USER_EXPLICIT,
            ownerSessionId = null,
            sourceSessionId = "session",
            sourceTurnId = "turn-$id",
            sourceMessageId = "message-$id",
            createdAt = at,
            updatedAt = at,
            retentionState = state,
            governanceKey = memoryGovernanceKey(MemoryScope.USER, null, subject),
            governanceSubject = subject,
            governanceValue = value,
        )
    }
}
