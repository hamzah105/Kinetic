package dev.kinetic.core

import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.memory.ControlledMemoryService
import dev.kinetic.core.memory.ExplicitMemoryCommandParser
import dev.kinetic.core.memory.MemoryCommandOperation
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryResolutionResult
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.memory.MemorySource
import dev.kinetic.core.memory.RememberResult
import dev.kinetic.core.memory.memoryGovernanceKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryGovernanceTest {
    @Test
    fun `parser recognizes bounded update and forget commands`() {
        val update = assertNotNull(
            ExplicitMemoryCommandParser.parse(
                "Update my preferred Kinetic test database to SQLite.",
            ),
        )
        assertEquals(MemoryCommandOperation.UPDATE, update.operation)
        assertEquals("preferred Kinetic test database", update.governanceSubject)
        assertEquals("SQLite", update.governanceValue)
        assertEquals("my preferred Kinetic test database is SQLite", update.content)

        val forget = assertNotNull(
            ExplicitMemoryCommandParser.parse("Forget my preferred Kinetic test database."),
        )
        assertEquals(MemoryCommandOperation.FORGET, forget.operation)
        assertEquals("preferred Kinetic test database", forget.governanceSubject)
    }

    @Test
    fun `production parser canonicalizes case punctuation and harmless whitespace across commands`() {
        val remember = command(
            "  ReMeMbEr   that   MY   preferred   Kinetic test database   IS   PostgreSQL!  ",
        )
        val update = command(
            "  uPdAtE   my   preferred Kinetic test database   TO   SQLite?  ",
        )

        assertEquals(MemoryCommandOperation.CREATE, remember.operation)
        assertEquals(MemoryCommandOperation.UPDATE, update.operation)
        assertEquals(remember.governanceSubject, update.governanceSubject)
        assertEquals(
            memoryGovernanceKey(MemoryScope.USER, null, remember.governanceSubject!!),
            memoryGovernanceKey(MemoryScope.USER, null, update.governanceSubject!!),
        )
        assertEquals(MemoryCategory.PREFERENCE, remember.category)
        assertEquals(MemoryCategory.PREFERENCE, update.category)
    }

    @Test
    fun `explicit update creates one active value and preserves superseded history`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)

        val original = assertIs<RememberResult.Created>(
            service.remember(
                command("Remember that my preferred Kinetic test database is PostgreSQL."),
                source("create"),
            ),
        )
        val update = assertIs<RememberResult.Replaced>(
            service.remember(
                command("Update my preferred Kinetic test database to SQLite."),
                source("update"),
            ),
        )

        assertEquals("SQLite", update.record.governanceValue)
        assertEquals(original.record.memoryId, update.record.supersedesMemoryId)
        assertEquals(listOf("SQLite"), fixture.memories.context("session-b").userMemories.map { it.governanceValue })
        val history = fixture.memories.observeUserMemories().first()
        assertEquals(2, history.size)
        assertEquals(MemoryRetentionState.SUPERSEDED, history.single { it.memoryId == original.record.memoryId }.retentionState)
        assertEquals(MemoryRetentionState.ACTIVE, history.single { it.memoryId == update.record.memoryId }.retentionState)
        assertTrue(fixture.journal.entries("session-a").any {
            (it.event as? JournalEvent.MemoryGovernanceChanged)?.action == "UPDATED"
        })

        val replacement = assertIs<RememberResult.Replaced>(
            service.remember(
                command("Replace my preferred Kinetic test database with Room."),
                source("replace"),
            ),
        )
        assertEquals("Room", replacement.record.governanceValue)
        val finalHistory = fixture.memories.observeUserMemories().first()
        assertEquals(MemoryRetentionState.SUPERSEDED, finalHistory.single { it.governanceValue == "SQLite" }.retentionState)
        assertEquals(MemoryRetentionState.ACTIVE, finalHistory.single { it.governanceValue == "Room" }.retentionState)
    }

    @Test
    fun `explicit update adopts matching legacy memory and supersedes it`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val legacy = legacyMemory(
            fixture,
            id = "legacy-postgres",
            content = "my preferred Kinetic test database is PostgreSQL.",
        )
        fixture.memories.create(legacy)
        val service = service(fixture)

        val update = assertIs<RememberResult.Replaced>(
            service.remember(
                command("Update my preferred Kinetic test database to SQLite."),
                source("legacy-update"),
            ),
        )

        assertEquals(1, update.supersededCount)
        assertEquals(listOf("SQLite"), fixture.memories.context("new-session").userMemories.map { it.governanceValue })
        val history = fixture.memories.observeUserMemories().first()
        val adopted = history.single { it.memoryId == legacy.memoryId }
        assertEquals("preferred Kinetic test database", adopted.governanceSubject)
        assertEquals("PostgreSQL", adopted.governanceValue)
        assertEquals(MemoryRetentionState.SUPERSEDED, adopted.retentionState)
        assertEquals(MemoryRetentionState.ACTIVE, history.single { it.governanceValue == "SQLite" }.retentionState)
    }

    @Test
    fun `explicit update to existing value supersedes another legacy current value`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)
        fixture.memories.create(
            legacyMemory(fixture, "legacy-postgres", "my preferred Kinetic test database is PostgreSQL."),
        )
        service.remember(
            command("Remember that my preferred Kinetic test database is SQLite."),
            source("sqlite-conflict"),
        )

        assertIs<RememberResult.Replaced>(
            service.remember(
                command("Update my preferred Kinetic test database to SQLite."),
                source("select-existing"),
            ),
        )
        val current = fixture.memories.context("new-session").userMemories
        assertEquals(listOf("SQLite"), current.map { it.governanceValue })
        assertEquals(MemoryRetentionState.ACTIVE, current.single().retentionState)
    }

    @Test
    fun `contradictory remember adopts legacy memory but preserves conflict`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        fixture.memories.create(
            legacyMemory(fixture, "legacy-editor", "my preferred Kinetic test editor is VS Code."),
        )

        assertIs<RememberResult.Conflict>(
            service(fixture).remember(
                command("Remember that my preferred Kinetic test editor is IntelliJ."),
                source("legacy-conflict"),
            ),
        )
        val current = fixture.memories.context("new-session").userMemories
        assertEquals(setOf("VS Code", "IntelliJ"), current.mapNotNull { it.governanceValue }.toSet())
        assertTrue(current.all { it.retentionState == MemoryRetentionState.CONFLICTED })
    }

    @Test
    fun `SESSION scoped natural language update cannot modify USER memory`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)
        service.remember(
            command("Remember that my preferred Kinetic test database is PostgreSQL."),
            source("user"),
        )
        service.remember(
            command("Remember for this conversation that my preferred Kinetic test database is H2."),
            source("session-create"),
        )
        assertIs<RememberResult.Replaced>(
            service.remember(
                command("Update for this conversation my preferred Kinetic test database to SQLite."),
                source("session-update"),
            ),
        )

        val context = fixture.memories.context("session-a")
        assertEquals(listOf("PostgreSQL"), context.userMemories.map { it.governanceValue })
        assertEquals(listOf("SQLite"), context.sessionMemories.map { it.governanceValue })
    }

    @Test
    fun `contradictory remember preserves both values until explicit resolution`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)

        service.remember(
            command("Remember that my preferred Kinetic test editor is VS Code."),
            source("first"),
        )
        val conflict = assertIs<RememberResult.Conflict>(
            service.remember(
                command("Remember that my preferred Kinetic test editor is IntelliJ."),
                source("second"),
            ),
        )
        val before = fixture.memories.context("session-b").userMemories
        assertEquals(setOf("VS Code", "IntelliJ"), before.mapNotNull { it.governanceValue }.toSet())
        assertTrue(before.all { it.retentionState == MemoryRetentionState.CONFLICTED })

        val resolved = assertIs<MemoryResolutionResult.Resolved>(
            service.resolve(conflict.record.memoryId, source("resolve")),
        )
        assertEquals("IntelliJ", resolved.selected.governanceValue)
        val after = fixture.memories.context("session-b").userMemories
        assertEquals(listOf("IntelliJ"), after.map { it.governanceValue })
        assertEquals(MemoryRetentionState.ACTIVE, after.single().retentionState)
    }

    @Test
    fun `ambiguous natural language forget refuses to erase a conflict`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)
        service.remember(
            command("Remember that my preferred Kinetic test editor is VS Code."),
            source("first"),
        )
        service.remember(
            command("Remember that my preferred Kinetic test editor is IntelliJ."),
            source("second"),
        )

        val result = service.remember(
            command("Forget my preferred Kinetic test editor."),
            source("forget"),
        )

        assertIs<RememberResult.Ambiguous>(result)
        assertEquals(2, fixture.memories.context("session-b").userMemories.size)
    }

    @Test
    fun `repeated update is idempotent and does not grow the chain`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)
        service.remember(
            command("Remember that my preferred Kinetic test database is PostgreSQL."),
            source("first"),
        )
        service.remember(
            command("Update my preferred Kinetic test database to SQLite."),
            source("update-one"),
        )
        assertIs<RememberResult.Duplicate>(
            service.remember(
                command("Update my preferred Kinetic test database to SQLite."),
                source("update-two"),
            ),
        )
        assertEquals(2, fixture.memories.observeUserMemories().first().size)
    }

    @Test
    fun `concurrent contradictory creates converge to one visible conflict set`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)

        val left = async {
            service.remember(
                command("Remember that my preferred Kinetic test editor is VS Code."),
                source("concurrent-left"),
            )
        }
        val right = async {
            service.remember(
                command("Remember that my preferred Kinetic test editor is IntelliJ."),
                source("concurrent-right"),
            )
        }
        listOf(left.await(), right.await())

        val current = fixture.memories.context("session-b").userMemories
        assertEquals(2, current.size)
        assertTrue(current.all { it.retentionState == MemoryRetentionState.CONFLICTED })
        assertEquals(setOf("VS Code", "IntelliJ"), current.mapNotNull { it.governanceValue }.toSet())
    }

    private fun service(fixture: RuntimeTestFixture) =
        ControlledMemoryService(fixture.memories, fixture.journal, fixture.ids, fixture.clock)

    @Test
    fun `derived duplicate cannot supersede explicit alternatives or adopt legacy governance`() = runTest {
        val fixture = RuntimeTestFixture()
        fixture.sessions.getOrCreate("session-a", fixture.clock.instant())
        val service = service(fixture)
        service.remember(command("Remember that my preferred editor is VS Code."), source("one"))
        service.remember(command("Remember that my preferred editor is IntelliJ."), source("two"))
        val before = fixture.memories.observeUserMemories().first()
        fixture.memories.govern(before.first().copy(memoryId = "derived", provenance = MemoryProvenance.USER_MESSAGE_DERIVED),
            dev.kinetic.core.memory.MemoryGovernanceWriteMode.REPLACE)
        assertEquals(before, fixture.memories.observeUserMemories().first())
        val legacy = legacyMemory(fixture, "legacy", "my preferred database is PostgreSQL")
        fixture.memories.create(legacy)
        fixture.memories.govern(legacy.copy(memoryId = "derived-db", provenance = MemoryProvenance.USER_MESSAGE_DERIVED,
            governanceKey = memoryGovernanceKey(MemoryScope.USER, null, "preferred database"),
            governanceSubject = "preferred database", governanceValue = "PostgreSQL"),
            dev.kinetic.core.memory.MemoryGovernanceWriteMode.REPLACE)
        assertEquals(legacy, fixture.memories.get("legacy"))
    }

    private fun command(value: String) = assertNotNull(ExplicitMemoryCommandParser.parse(value))

    private fun source(label: String) = MemorySource("session-a", "turn-$label", "message-$label")

    private fun legacyMemory(
        fixture: RuntimeTestFixture,
        id: String,
        content: String,
    ) = MemoryRecord(
        memoryId = id,
        scope = MemoryScope.USER,
        category = MemoryCategory.PREFERENCE,
        content = content,
        provenance = MemoryProvenance.USER_EXPLICIT,
        ownerSessionId = null,
        sourceSessionId = "legacy-session",
        sourceTurnId = "legacy-turn-$id",
        sourceMessageId = "legacy-message-$id",
        createdAt = fixture.clock.instant(),
        updatedAt = fixture.clock.instant(),
    )
}
