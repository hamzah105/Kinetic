package dev.kinetic.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.agent.RecoveryInterrupted
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.logging.SessionEventJournal
import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.memory.ControlledMemoryService
import dev.kinetic.core.memory.ExplicitMemoryCommand
import dev.kinetic.core.memory.ExplicitMemoryCommandParser
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryCreateResult
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryResolutionResult
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.memory.MemorySource
import dev.kinetic.core.memory.RememberResult
import dev.kinetic.core.context.SessionSummary
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.policy.CapabilityMetadata
import dev.kinetic.core.policy.CapabilityCategory
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.RiskLevel
import dev.kinetic.core.session.DurableApprovalStatus
import dev.kinetic.core.session.DurableEffectStatus
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.tools.ProtectedDemoInput
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.OpenHttpsUrlInput
import dev.kinetic.core.tools.OpenDialerInput
import dev.kinetic.core.tools.ToolInput
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolAvailabilityStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun sessions_messages_and_structured_journal_survive_database_reopen() = runBlocking {
        withFreshDatabase("room-session-reopen.db") { name ->
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                store.getOrCreate("session-a", now)
                store.getOrCreate("session-b", now)
                store.appendMessage(
                    "session-a",
                    AgentMessage(
                        messageId = "z-first-by-insertion",
                        turnId = "turn-a",
                        role = MessageRole.USER,
                        content = "token=should-not-persist",
                        createdAt = now,
                    ),
                )
                store.appendMessage(
                    "session-a",
                    AgentMessage(
                        messageId = "a-second-by-insertion",
                        turnId = "turn-a",
                        role = MessageRole.ASSISTANT,
                        content = "ordered response",
                        createdAt = now,
                    ),
                )
                store.appendMessage(
                    "session-b",
                    AgentMessage(
                        messageId = "message-b",
                        turnId = "turn-b",
                        role = MessageRole.USER,
                        content = "isolated",
                        createdAt = now,
                    ),
                )
                store.appendJournalEvent(
                    "session-a",
                    "turn-a",
                    "journal-1",
                    now,
                    JournalEvent.ToolRequested("call-a", "echo", "textLength=7"),
                )
                store.appendJournalEvent(
                    "session-a",
                    "turn-a",
                    "journal-2",
                    now,
                    JournalEvent.StateTransition(RuntimePhase.THINKING, RuntimePhase.EXECUTING),
                )
            }

            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val restored = assertNotNull(store.get("session-a"))

                assertEquals(
                    listOf("token=[REDACTED]", "ordered response"),
                    restored.messages.map { it.content },
                )
                assertEquals(listOf(1L, 2L), restored.messages.map { it.sequence })
                assertEquals(listOf(1L, 2L), restored.journal.map { it.sequence })
                assertIs<JournalEvent.ToolRequested>(restored.journal[0].event)
                assertEquals(
                    JournalEvent.StateTransition(RuntimePhase.THINKING, RuntimePhase.EXECUTING),
                    restored.journal[1].event,
                )
                assertEquals("isolated", store.get("session-b")!!.messages.single().content)
                assertTrue(restored.messages.none { it.content == "isolated" })
            }
        }
    }

    @Test
    fun capability_availability_and_dispatch_failure_audit_survive_without_arguments() =
        runBlocking {
            withFreshDatabase("room-capability-audit-reopen.db") { name ->
                withOpenDatabase(name) { database ->
                    val store = RoomSessionStore(database)
                    store.getOrCreate("capability-session", now)
                    store.appendJournalEvent(
                        "capability-session",
                        "turn-capability",
                        "journal-availability",
                        now,
                        JournalEvent.ToolAvailabilityChecked(
                            "call-capability",
                            "compose_email",
                            ToolAvailabilityStatus.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
                        ),
                    )
                    store.appendJournalEvent(
                        "capability-session",
                        "turn-capability",
                        "journal-dispatch-failed",
                        now,
                        JournalEvent.ToolDispatchFailed(
                            "call-capability",
                            "compose_email",
                            "UNAVAILABLE_NO_FOREGROUND_ACTIVITY",
                        ),
                    )
                }

                withOpenDatabase(name) { database ->
                    val journal = assertNotNull(
                        RoomSessionStore(database).get("capability-session"),
                    ).journal
                    assertEquals(
                        JournalEvent.ToolAvailabilityChecked(
                            "call-capability",
                            "compose_email",
                            ToolAvailabilityStatus.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
                        ),
                        journal[0].event,
                    )
                    assertEquals(
                        JournalEvent.ToolDispatchFailed(
                            "call-capability",
                            "compose_email",
                            "UNAVAILABLE_NO_FOREGROUND_ACTIVITY",
                        ),
                        journal[1].event,
                    )
                    val auditText = journal.joinToString()
                    assertFalse(auditText.contains("owner@example.com"))
                    assertFalse(auditText.contains("private email body"))
                }
            }
        }

    @Test
    fun completed_pending_and_rejected_run_records_restore_exactly() = runBlocking {
        withFreshDatabase("room-run-reopen.db") { name ->
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val ledger = RoomRunLedger(database)

                seedTurn(store, ledger, "completed")
                ledger.prepareEffect("completed", "turn-completed", "call-completed", "echo", now)
                assertTrue(ledger.markExecuting("turn-completed", "call-completed", now))
                ledger.completeEffectAndTurn("turn-completed", "call-completed", "durable output", now)

                seedTurn(store, ledger, "pending")
                val pending = approval("pending")
                ledger.prepareEffect("pending", pending.turnId, pending.toolCall.callId, pending.toolCall.toolId, now)
                ledger.requestApproval(pending)

                seedTurn(store, ledger, "rejected")
                val rejected = approval("rejected")
                ledger.prepareEffect("rejected", rejected.turnId, rejected.toolCall.callId, rejected.toolCall.toolId, now)
                ledger.requestApproval(rejected)
                assertFalse(
                    ledger.resolveApproval(
                        rejected.approvalId,
                        rejected.turnId,
                        "wrong-call",
                        ApprovalDecision.REJECT,
                        now,
                    ),
                )
                assertTrue(
                    ledger.resolveApproval(
                        rejected.approvalId,
                        rejected.turnId,
                        rejected.toolCall.callId,
                        ApprovalDecision.REJECT,
                        now,
                    ),
                )
            }

            withOpenDatabase(name) { database ->
                val ledger = RoomRunLedger(database)
                val completed = assertNotNull(ledger.latestRun("completed"))
                val pending = assertNotNull(ledger.latestRun("pending"))
                val rejected = assertNotNull(ledger.latestRun("rejected"))

                assertEquals(DurableTurnStatus.COMPLETED, completed.turn.status)
                assertEquals("durable output", completed.turn.completionSummary)
                assertEquals(DurableEffectStatus.COMPLETED, completed.effect!!.status)
                assertEquals("durable output", completed.effect!!.outputSummary)

                assertEquals(DurableTurnStatus.WAITING_FOR_APPROVAL, pending.turn.status)
                assertEquals(DurableApprovalStatus.PENDING, pending.approval!!.status)
                assertEquals(ProtectedDemoInput("pending action"), pending.approval!!.request.toolCall.input)

                assertEquals(DurableTurnStatus.WAITING_FOR_APPROVAL, rejected.turn.status)
                assertEquals(DurableApprovalStatus.REJECTED, rejected.approval!!.status)
                assertEquals("call-rejected", rejected.approval!!.request.toolCall.callId)
            }
        }
    }

    @Test
    fun duplicate_call_identity_survives_reopen_and_completed_effect_is_not_downgraded() = runBlocking {
        withFreshDatabase("room-effect-identity.db") { name ->
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val ledger = RoomRunLedger(database)
                seedTurn(store, ledger, "identity")
                assertTrue(
                    ledger.prepareEffect(
                        "identity",
                        "turn-identity",
                        "durable-call",
                        "echo",
                        now,
                    ),
                )
                assertTrue(ledger.markExecuting("turn-identity", "durable-call", now))
                ledger.completeEffect("turn-identity", "durable-call", "done", now)
            }

            withOpenDatabase(name) { database ->
                val ledger = RoomRunLedger(database)
                assertFalse(
                    ledger.prepareEffect(
                        "identity",
                        "turn-identity",
                        "durable-call",
                        "echo",
                        now,
                    ),
                )
                ledger.failTurn(
                    "turn-identity",
                    RecoveryInterrupted(RuntimePhase.EXECUTING),
                    now,
                )
                val restored = assertNotNull(ledger.latestRun("identity"))
                assertEquals(DurableTurnStatus.FAILED, restored.turn.status)
                assertEquals(DurableEffectStatus.COMPLETED, restored.effect?.status)
                assertEquals("done", restored.effect?.outputSummary)
            }
        }
    }

    @Test
    fun Android_approval_restores_exact_arguments_metadata_and_effect_binding() = runBlocking {
        withFreshDatabase("room-android-approval.db") { name ->
            val original = OpenHttpsUrlInput("https://example.com/path")
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val ledger = RoomRunLedger(database)
                seedTurn(store, ledger, "android")
                assertTrue(
                    ledger.prepareEffect(
                        "android",
                        "turn-android",
                        "call-android",
                        "open_https_url",
                        now,
                        original.authorizationBinding(),
                    ),
                )
                val request = ApprovalRequest(
                    approvalId = "approval-android",
                    sessionId = "android",
                    turnId = "turn-android",
                    toolCall = ToolCall("call-android", "open_https_url", original),
                    capability = CapabilityMetadata(
                        riskLevel = RiskLevel.CONFIRM,
                        requiresConfirmation = true,
                        distributionAvailability = setOf(DistributionProfile.PLAY_CORE),
                        category = CapabilityCategory.EXTERNAL_NAVIGATION,
                        crossesApplicationBoundary = true,
                    ),
                    requestedAt = now,
                )
                ledger.requestApproval(request)
                assertTrue(
                    ledger.resolveApproval(
                        request.approvalId,
                        request.turnId,
                        request.toolCall.callId,
                        ApprovalDecision.APPROVE,
                        now,
                    ),
                )
                assertFalse(
                    ledger.markExecuting(
                        request.turnId,
                        request.toolCall.callId,
                        now,
                        OpenHttpsUrlInput("https://different.example").authorizationBinding(),
                    ),
                )
            }

            withOpenDatabase(name) { database ->
                val restored = assertNotNull(RoomRunLedger(database).latestRun("android"))
                assertEquals(original, restored.approval?.request?.toolCall?.input)
                assertEquals(
                    CapabilityCategory.EXTERNAL_NAVIGATION,
                    restored.approval?.request?.capability?.category,
                )
                assertTrue(restored.approval?.request?.capability?.crossesApplicationBoundary == true)
                assertEquals(original.authorizationBinding(), restored.effect?.inputBinding)
                assertEquals(DurableEffectStatus.PENDING, restored.effect?.status)
            }
        }
    }

    @Test
    fun Phase3B_pending_approvals_restore_exact_private_arguments_and_bindings() = runBlocking {
        withFreshDatabase("room-phase3b-approvals.db") { name ->
            val cases = listOf(
                Triple(
                    "clipboard",
                    "copy_text_to_clipboard",
                    CopyTextToClipboardInput("KINETIC_CLIPBOARD_OK") as ToolInput,
                ),
                Triple("dialer", "open_dialer", OpenDialerInput("+923001234567") as ToolInput),
                Triple(
                    "email",
                    "compose_email",
                    ComposeEmailInput(
                        "test@example.com",
                        "KINETIC_EMAIL_OK",
                        "Hello from Kinetic",
                    ) as ToolInput,
                ),
            )
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val ledger = RoomRunLedger(database)
                cases.forEach { (sessionId, toolId, input) ->
                    seedTurn(store, ledger, sessionId)
                    val turnId = "turn-$sessionId"
                    val callId = "call-$sessionId"
                    assertTrue(
                        ledger.prepareEffect(
                            sessionId,
                            turnId,
                            callId,
                            toolId,
                            now,
                            input.authorizationBinding(),
                        ),
                    )
                    ledger.requestApproval(
                        ApprovalRequest(
                            approvalId = "approval-$sessionId",
                            sessionId = sessionId,
                            turnId = turnId,
                            toolCall = ToolCall(callId, toolId, input),
                            capability = CapabilityMetadata(
                                riskLevel = RiskLevel.CONFIRM,
                                requiresConfirmation = true,
                                distributionAvailability = setOf(DistributionProfile.PLAY_CORE),
                                category = if (toolId == "copy_text_to_clipboard") {
                                    CapabilityCategory.CLIPBOARD_WRITE
                                } else {
                                    CapabilityCategory.USER_MEDIATED_COMMUNICATION
                                },
                                crossesApplicationBoundary = true,
                            ),
                            requestedAt = now,
                        ),
                    )
                }
            }

            withOpenDatabase(name) { database ->
                val ledger = RoomRunLedger(database)
                cases.forEach { (sessionId, toolId, input) ->
                    val restored = assertNotNull(ledger.latestRun(sessionId))
                    assertEquals(toolId, restored.approval?.request?.toolCall?.toolId)
                    assertEquals(input, restored.approval?.request?.toolCall?.input)
                    assertEquals(input.authorizationBinding(), restored.effect?.inputBinding)
                    assertEquals(DurableEffectStatus.PENDING, restored.effect?.status)
                }
            }
        }
    }

    @Test
    fun USER_and_SESSION_memories_survive_reopen_with_scope_isolation_and_provenance() = runBlocking {
        withFreshDatabase("room-memory-reopen.db") { name ->
            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                assertIs<MemoryCreateResult.Created>(
                    repository.create(memory("user-preference", MemoryScope.USER, "session-a")),
                )
                repository.create(memory("session-a-only", MemoryScope.SESSION, "session-a", "a"))
                repository.create(memory("session-b-only", MemoryScope.SESSION, "session-b", "b"))
            }

            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                val contextA = repository.context("session-a")
                val contextB = repository.context("session-b")
                assertEquals(listOf("user-preference"), contextA.userMemories.map { it.content })
                assertEquals(listOf("session-a-only"), contextA.sessionMemories.map { it.content })
                assertEquals(listOf("user-preference"), contextB.userMemories.map { it.content })
                assertEquals(listOf("session-b-only"), contextB.sessionMemories.map { it.content })
                assertEquals(MemoryProvenance.USER_EXPLICIT, contextA.userMemories.single().provenance)
                assertEquals(MemoryCategory.PREFERENCE, contextA.userMemories.single().category)
            }
        }
    }

    @Test
    fun exact_duplicate_memory_is_reused_after_database_reopen() = runBlocking {
        withFreshDatabase("room-memory-duplicate.db") { name ->
            withOpenDatabase(name) { database ->
                RoomMemoryRepository(database).create(
                    memory("Use   Kotlin", MemoryScope.USER, "session-a"),
                )
            }
            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                val duplicate = repository.create(
                    memory(" use kotlin ", MemoryScope.USER, "session-b", "duplicate"),
                )
                assertIs<MemoryCreateResult.Duplicate>(duplicate)
                assertEquals(1, repository.context("session-b").userMemories.size)
            }
        }
    }

    @Test
    fun individual_and_scoped_memory_deletion_survive_reopen() = runBlocking {
        withFreshDatabase("room-memory-delete.db") { name ->
            val deleteId = "delete-me"
            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                repository.create(memory("delete", MemoryScope.USER, "session-a", deleteId))
                repository.create(memory("keep-user", MemoryScope.USER, "session-a", "keep-user"))
                repository.create(memory("clear-session", MemoryScope.SESSION, "session-a", "clear-session"))
                repository.create(memory("keep-session", MemoryScope.SESSION, "session-b", "keep-session"))
                assertTrue(repository.delete(deleteId))
                assertEquals(1, repository.clearSession("session-a"))
            }
            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                assertEquals(listOf("keep-user"), repository.context("session-a").userMemories.map { it.content })
                assertTrue(repository.context("session-a").sessionMemories.isEmpty())
                assertEquals(listOf("keep-session"), repository.context("session-b").sessionMemories.map { it.content })
                assertEquals(1, repository.clearUser())
            }
            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                assertTrue(repository.context("session-b").userMemories.isEmpty())
                assertEquals(1, repository.context("session-b").sessionMemories.size)
            }
        }
    }

    @Test
    fun rejected_secret_is_absent_from_memory_table_and_safe_journal_after_reopen() = runBlocking {
        withFreshDatabase("room-memory-secret.db") { name ->
            val secret = "sk-secretRoomValue123456789"
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                store.getOrCreate("session-a", now)
                val ids = SequenceIds()
                val journal = SessionEventJournal(
                    store,
                    ids,
                    Clock.fixed(now, ZoneOffset.UTC),
                )
                val service = ControlledMemoryService(
                    RoomMemoryRepository(database),
                    journal,
                    ids,
                    Clock.fixed(now, ZoneOffset.UTC),
                )
                val result = service.remember(
                    ExplicitMemoryCommand(
                        MemoryScope.USER,
                        MemoryCategory.FACT,
                        "API key: $secret",
                    ),
                    MemorySource("session-a", "memory-turn", "memory-message"),
                )
                assertIs<RememberResult.Rejected>(result)
            }

            withOpenDatabase(name) { database ->
                assertTrue(RoomMemoryRepository(database).context("session-a").ordered.isEmpty())
                val restored = assertNotNull(RoomSessionStore(database).get("session-a"))
                assertIs<JournalEvent.MemoryRejected>(restored.journal.single().event)
                assertFalse(restored.journal.toString().contains(secret))
                database.openHelper.readableDatabase
                    .query("SELECT COUNT(*) FROM memories WHERE content LIKE '%secretRoomValue%'")
                    .use { cursor ->
                        cursor.moveToFirst()
                        assertEquals(0, cursor.getInt(0))
                    }
            }
        }
    }

    @Test
    fun memory_injection_audit_round_trips_without_memory_content() = runBlocking {
        withFreshDatabase("room-memory-audit.db") { name ->
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                store.getOrCreate("session-a", now)
                store.appendJournalEvent(
                    "session-a",
                    "turn-a",
                    "memory-audit",
                    now,
                    JournalEvent.MemoryInjected(listOf("memory-1", "memory-2"), 1, 1),
                )
            }
            withOpenDatabase(name) { database ->
                val event = assertIs<JournalEvent.MemoryInjected>(
                    RoomSessionStore(database).get("session-a")!!.journal.single().event,
                )
                assertEquals(listOf("memory-1", "memory-2"), event.memoryIds)
                assertEquals(1, event.userCount)
                assertEquals(1, event.sessionCount)
                assertFalse(event.toString().contains("private memory content"))
            }
        }
    }

    @Test
    fun governed_update_conflict_and_resolution_survive_database_reopen() = runBlocking {
        withFreshDatabase("room-memory-governance.db") { name ->
            var intellijId = ""
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                store.getOrCreate("session-a", now)
                val ids = SequenceIds()
                val journal = SessionEventJournal(store, ids, Clock.fixed(now, ZoneOffset.UTC))
                val service = ControlledMemoryService(
                    RoomMemoryRepository(database),
                    journal,
                    ids,
                    Clock.fixed(now, ZoneOffset.UTC),
                )
                service.remember(
                    requireNotNull(
                        ExplicitMemoryCommandParser.parse(
                            "Remember that my preferred Kinetic test database is PostgreSQL.",
                        ),
                    ),
                    MemorySource("session-a", "create-db", "message-db"),
                )
                assertIs<RememberResult.Replaced>(
                    service.remember(
                        requireNotNull(
                            ExplicitMemoryCommandParser.parse(
                                "Update my preferred Kinetic test database to SQLite.",
                            ),
                        ),
                        MemorySource("session-a", "update-db", "message-update-db"),
                    ),
                )
                service.remember(
                    requireNotNull(
                        ExplicitMemoryCommandParser.parse(
                            "Remember that my preferred Kinetic test editor is VS Code.",
                        ),
                    ),
                    MemorySource("session-a", "create-editor", "message-editor"),
                )
                intellijId = assertIs<RememberResult.Conflict>(
                    service.remember(
                        requireNotNull(
                            ExplicitMemoryCommandParser.parse(
                                "Remember that my preferred Kinetic test editor is IntelliJ.",
                            ),
                        ),
                        MemorySource("session-a", "conflict-editor", "message-conflict-editor"),
                    ),
                ).record.memoryId
            }

            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                val context = repository.context("new-session")
                assertTrue(context.userMemories.none { it.governanceValue == "PostgreSQL" })
                assertTrue(context.userMemories.any { it.governanceValue == "SQLite" })
                assertEquals(
                    2,
                    context.userMemories.count {
                        it.retentionState == MemoryRetentionState.CONFLICTED
                    },
                )
                assertIs<MemoryResolutionResult.Resolved>(repository.resolve(intellijId, now.plusSeconds(1)))
            }

            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                val context = repository.context("new-session")
                assertTrue(context.userMemories.any { it.governanceValue == "SQLite" })
                assertTrue(context.userMemories.any { it.governanceValue == "IntelliJ" })
                assertTrue(context.userMemories.none { it.governanceValue == "VS Code" })
                val history = repository.observeUserMemories().first()
                assertTrue(history.any {
                    it.governanceValue == "PostgreSQL" &&
                        it.retentionState == MemoryRetentionState.SUPERSEDED
                })
                assertTrue(history.any {
                    it.governanceValue == "VS Code" &&
                        it.retentionState == MemoryRetentionState.SUPERSEDED
                })
            }
        }
    }

    @Test
    fun explicit_update_adopts_legacy_ungoverned_memory_and_survives_database_reopen() = runBlocking {
        withFreshDatabase("room-memory-legacy-governance.db") { name ->
            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                store.getOrCreate("session-a", now)
                RoomMemoryRepository(database).create(
                    memory(
                        content = "my preferred Kinetic test database is PostgreSQL.",
                        scope = MemoryScope.USER,
                        sessionId = "legacy-session",
                        id = "legacy-postgres",
                    ),
                )
            }

            withOpenDatabase(name) { database ->
                val store = RoomSessionStore(database)
                val ids = SequenceIds()
                val service = ControlledMemoryService(
                    RoomMemoryRepository(database),
                    SessionEventJournal(store, ids, Clock.fixed(now, ZoneOffset.UTC)),
                    ids,
                    Clock.fixed(now, ZoneOffset.UTC),
                )
                assertIs<RememberResult.Replaced>(
                    service.remember(
                        requireNotNull(
                            ExplicitMemoryCommandParser.parse(
                                "Update my preferred Kinetic test database to SQLite.",
                            ),
                        ),
                        MemorySource("session-a", "legacy-update", "legacy-update-message"),
                    ),
                )
            }

            withOpenDatabase(name) { database ->
                val repository = RoomMemoryRepository(database)
                assertEquals(
                    listOf("SQLite"),
                    repository.context("new-session").userMemories.map { it.governanceValue },
                )
                val history = repository.observeUserMemories().first()
                val postgres = history.single { it.memoryId == "legacy-postgres" }
                assertEquals("preferred Kinetic test database", postgres.governanceSubject)
                assertEquals("PostgreSQL", postgres.governanceValue)
                assertEquals(MemoryRetentionState.SUPERSEDED, postgres.retentionState)
                assertEquals(
                    MemoryRetentionState.ACTIVE,
                    history.single { it.governanceValue == "SQLite" }.retentionState,
                )
            }
        }
    }

    @Test
    fun completed_session_summaries_survive_reopen_are_isolated_and_do_not_replace_history() =
        runBlocking {
            withFreshDatabase("room-summary-reopen.db") { name ->
                withOpenDatabase(name) { database ->
                    val store = RoomSessionStore(database)
                    store.getOrCreate("session-a", now)
                    store.getOrCreate("session-b", now)
                    store.appendMessage(
                        "session-a",
                        AgentMessage("original", "turn", MessageRole.USER, "ORBIT-742", now),
                    )
                    val summaries = RoomSessionSummaryRepository(database)
                    summaries.save(summary("summary-a1", "session-a", 2, "a"))
                    summaries.save(summary("summary-a2", "session-a", 4, "b"))
                    summaries.save(summary("summary-b", "session-b", 2, "c"))
                }

                withOpenDatabase(name) { database ->
                    val summaries = RoomSessionSummaryRepository(database)
                    assertEquals("summary-a2", summaries.latest("session-a")?.summaryId)
                    assertEquals("summary-b", summaries.latest("session-b")?.summaryId)
                    assertEquals("ORBIT-742", RoomSessionStore(database).get("session-a")!!.messages.single().content)
                    assertTrue(summaries.delete("summary-a2"))
                    assertEquals("summary-a1", summaries.latest("session-a")?.summaryId)
                    assertEquals("summary-b", summaries.latest("session-b")?.summaryId)
                }
            }
        }

    private suspend fun seedTurn(
        store: RoomSessionStore,
        ledger: RoomRunLedger,
        sessionId: String,
    ) {
        store.getOrCreate(sessionId, now)
        ledger.beginTurn(
            DurableTurnRecord(
                turnId = "turn-$sessionId",
                sessionId = sessionId,
                requestId = "request-$sessionId",
                requestSummary = "test",
                status = DurableTurnStatus.THINKING,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun approval(sessionId: String) = ApprovalRequest(
        approvalId = "approval-$sessionId",
        sessionId = sessionId,
        turnId = "turn-$sessionId",
        toolCall = ToolCall(
            callId = "call-$sessionId",
            toolId = "protected_demo_tool",
            input = ProtectedDemoInput("$sessionId action"),
        ),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.CONFIRM,
            requiresConfirmation = true,
            distributionAvailability = setOf(DistributionProfile.PLAY_CORE),
        ),
        requestedAt = now,
    )

    private fun memory(
        content: String,
        scope: MemoryScope,
        sessionId: String,
        id: String = "memory-${content.hashCode()}",
    ) = MemoryRecord(
        memoryId = id,
        scope = scope,
        category = if (scope == MemoryScope.USER) MemoryCategory.PREFERENCE else MemoryCategory.TASK_CONTEXT,
        content = content,
        provenance = MemoryProvenance.USER_EXPLICIT,
        ownerSessionId = sessionId.takeIf { scope == MemoryScope.SESSION },
        sourceSessionId = sessionId,
        sourceTurnId = "source-turn-$id",
        sourceMessageId = "source-message-$id",
        createdAt = now,
        updatedAt = now,
    )

    private fun summary(id: String, sessionId: String, covered: Long, digestChar: String) =
        SessionSummary(
            summaryId = id,
            sessionId = sessionId,
            content = "Derived context through $covered",
            firstCoveredMessageSequence = 1,
            lastCoveredMessageSequence = covered,
            sourceMessageCount = covered.toInt(),
            createdAt = now.plusSeconds(covered),
            sourceDigest = digestChar.repeat(64),
        )

    private class SequenceIds : IdGenerator {
        private var value = 0
        override fun nextId(prefix: String): String = "$prefix-${++value}"
    }

    private suspend fun withFreshDatabase(
        name: String,
        block: suspend (String) -> Unit,
    ) {
        context.deleteDatabase(name)
        try {
            block(name)
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun <T> withOpenDatabase(
        name: String,
        block: suspend (KineticDatabase) -> T,
    ): T {
        val database = KineticDatabase.open(context, name)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }
}
