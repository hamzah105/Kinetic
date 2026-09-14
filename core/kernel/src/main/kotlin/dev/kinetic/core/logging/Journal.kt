package dev.kinetic.core.logging

import dev.kinetic.core.agent.AgentError
import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.session.SessionStore
import dev.kinetic.core.tools.ToolAvailabilityStatus
import java.time.Clock
import java.time.Instant

sealed interface JournalEvent {
    data class UserRequestRecorded(val requestId: String, val summary: String) : JournalEvent
    data class ModelResponseRecorded(
        val responseId: String,
        val summary: String,
        val toolCallIds: List<String>,
    ) : JournalEvent

    data class ToolRequested(
        val callId: String,
        val toolId: String,
        val inputSummary: String,
    ) : JournalEvent

    data class ApprovalRequested(val approvalId: String, val callId: String) : JournalEvent
    data class ApprovalAccepted(val approvalId: String, val callId: String) : JournalEvent
    data class ApprovalRejected(val approvalId: String, val callId: String) : JournalEvent
    data class ToolAvailabilityChecked(
        val callId: String,
        val toolId: String,
        val status: ToolAvailabilityStatus,
    ) : JournalEvent

    data class ToolExecutionStarted(val callId: String, val toolId: String) : JournalEvent
    data class ToolExecutionCompleted(
        val callId: String,
        val toolId: String,
        val outputSummary: String,
    ) : JournalEvent

    data class ToolDispatchFailed(
        val callId: String,
        val toolId: String,
        val failureCode: String,
    ) : JournalEvent

    data class MemoryCreated(
        val memoryId: String,
        val scope: MemoryScope,
        val category: MemoryCategory,
        val contentLength: Int,
    ) : JournalEvent

    data class MemoryDuplicateReused(
        val memoryId: String,
        val scope: MemoryScope,
        val category: MemoryCategory,
    ) : JournalEvent

    data class MemoryDeleted(
        val memoryId: String,
        val scope: MemoryScope,
        val category: MemoryCategory,
    ) : JournalEvent

    data class MemoryRejected(
        val scope: MemoryScope,
        val category: MemoryCategory,
        val reasonCode: String,
    ) : JournalEvent

    data class MemoryInjected(
        val memoryIds: List<String>,
        val userCount: Int,
        val sessionCount: Int,
    ) : JournalEvent {
        init {
            require(memoryIds.size <= 8)
        }
    }

    data class MemoryCleared(val scope: MemoryScope, val count: Int) : JournalEvent

    data class MemoryGovernanceChanged(
        val action: String,
        val memoryId: String?,
        val relatedMemoryIds: List<String>,
        val governanceKeyHashPrefix: String,
    ) : JournalEvent {
        init {
            require(
                action in setOf(
                    "UPDATED",
                    "CONFLICT_DETECTED",
                    "RESOLVED",
                    "FORGET_REQUESTED",
                    "FORGOTTEN",
                    "FORGET_REFUSED",
                ),
            )
            require(relatedMemoryIds.size <= 8)
            require(governanceKeyHashPrefix.length <= 12)
        }
    }

    data class ErrorRecorded(val code: String, val userMessage: String) : JournalEvent
    data class CancellationRecorded(val reasonCode: String) : JournalEvent
    data class StateTransition(val from: RuntimePhase, val to: RuntimePhase) : JournalEvent
}

data class JournalEntry(
    val entryId: String,
    val sessionId: String,
    val turnId: String,
    val sequence: Long,
    val occurredAt: Instant,
    val event: JournalEvent,
)

interface EventJournal {
    suspend fun record(sessionId: String, turnId: String, event: JournalEvent): JournalEntry
    suspend fun entries(sessionId: String): List<JournalEntry>
}

/** Stores structured events inside the owning session; sequence assignment is atomic in the store. */
class SessionEventJournal(
    private val sessionStore: SessionStore,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : EventJournal {
    override suspend fun record(
        sessionId: String,
        turnId: String,
        event: JournalEvent,
    ): JournalEntry = sessionStore.appendJournalEvent(
        sessionId = sessionId,
        turnId = turnId,
        entryId = idGenerator.nextId("journal"),
        occurredAt = clock.instant(),
        event = event,
    )

    override suspend fun entries(sessionId: String): List<JournalEntry> =
        sessionStore.get(sessionId)?.journal.orEmpty()
}

object JournalSanitizer {
    private val credentialPattern = Regex(
        pattern = "(?i)(api[_-]?key|token|password|secret|authorization)\\s*[:=]\\s*([^\\s,;]+)",
    )

    fun redact(value: String, maxLength: Int = 240): String {
        val redacted = credentialPattern.replace(value) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        return redacted.take(maxLength)
    }

    fun error(error: AgentError): JournalEvent.ErrorRecorded =
        JournalEvent.ErrorRecorded(error.code, error.userMessage)
}
