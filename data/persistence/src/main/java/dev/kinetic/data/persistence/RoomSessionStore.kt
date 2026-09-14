package dev.kinetic.data.persistence

import androidx.room.withTransaction
import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.logging.JournalEntry
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.tools.ToolAvailabilityStatus
import dev.kinetic.core.logging.JournalSanitizer
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.session.AgentSession
import dev.kinetic.core.session.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant

internal class RoomSessionStore(
    private val database: KineticDatabase,
) : SessionStore {
    private val dao = database.kineticDao()

    override suspend fun getOrCreate(sessionId: String, now: Instant): AgentSession =
        database.withTransaction {
            val existing = dao.session(sessionId)
            if (existing == null) {
                dao.insertSession(
                    SessionEntity(
                        sessionId = sessionId,
                        createdAtEpochMillis = now.toEpochMilli(),
                        updatedAtEpochMillis = now.toEpochMilli(),
                    ),
                )
            }
            loadSession(sessionId) ?: error("Session creation did not persist")
        }

    override suspend fun get(sessionId: String): AgentSession? =
        database.withTransaction { loadSession(sessionId) }

    override fun observe(sessionId: String): Flow<AgentSession?> = combine(
        dao.observeSession(sessionId),
        dao.observeMessages(sessionId),
        dao.observeJournalEntries(sessionId),
    ) { session, messages, entries ->
        session?.toDomain(
            messages = messages.map(MessageEntity::toDomain),
            journal = entries.map(JournalEntryEntity::toDomain),
        )
    }

    override suspend fun appendMessage(sessionId: String, message: AgentMessage): AgentSession =
        database.withTransaction {
            val session = requireNotNull(dao.session(sessionId)) {
                "Session $sessionId must be created before appending a message"
            }
            val persistedMessage = message.copy(
                content = JournalSanitizer.redact(message.content, maxLength = 32_000),
                sequence = dao.nextMessageSequence(sessionId),
            )
            dao.insertMessage(persistedMessage.toEntity(sessionId))
            dao.updateSession(
                session.copy(
                    updatedAtEpochMillis = maxOf(
                        session.updatedAtEpochMillis,
                        message.createdAt.toEpochMilli(),
                    ),
                ),
            )
            requireNotNull(loadSession(sessionId))
        }

    override suspend fun appendJournalEvent(
        sessionId: String,
        turnId: String,
        entryId: String,
        occurredAt: Instant,
        event: JournalEvent,
    ): JournalEntry = database.withTransaction {
        val session = requireNotNull(dao.session(sessionId)) {
            "Session $sessionId must be created before appending a journal event"
        }
        val entity = event.toEntity(
            entryId = entryId,
            sessionId = sessionId,
            turnId = turnId,
            sequence = dao.nextJournalSequence(sessionId),
            occurredAt = occurredAt,
        )
        dao.insertJournalEntry(entity)
        dao.updateSession(
            session.copy(
                updatedAtEpochMillis = maxOf(
                    session.updatedAtEpochMillis,
                    occurredAt.toEpochMilli(),
                ),
            ),
        )
        entity.toDomain()
    }

    private suspend fun loadSession(sessionId: String): AgentSession? {
        val session = dao.session(sessionId) ?: return null
        return session.toDomain(
            messages = dao.messages(sessionId).map(MessageEntity::toDomain),
            journal = dao.journalEntries(sessionId).map(JournalEntryEntity::toDomain),
        )
    }
}

private enum class StoredJournalEventType {
    USER_REQUEST_RECORDED,
    MODEL_RESPONSE_RECORDED,
    TOOL_REQUESTED,
    APPROVAL_REQUESTED,
    APPROVAL_ACCEPTED,
    APPROVAL_REJECTED,
    TOOL_AVAILABILITY_CHECKED,
    TOOL_EXECUTION_STARTED,
    TOOL_EXECUTION_COMPLETED,
    TOOL_DISPATCH_FAILED,
    MEMORY_CREATED,
    MEMORY_DUPLICATE_REUSED,
    MEMORY_DELETED,
    MEMORY_REJECTED,
    MEMORY_INJECTED,
    MEMORY_CLEARED,
    MEMORY_GOVERNANCE_CHANGED,
    ERROR_RECORDED,
    CANCELLATION_RECORDED,
    STATE_TRANSITION,
}

private const val LIST_SEPARATOR = "\u001F"

private fun AgentMessage.toEntity(sessionId: String) = MessageEntity(
    messageId = messageId,
    sessionId = sessionId,
    turnId = turnId,
    role = role.name,
    content = content,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    sequence = sequence,
)

private fun MessageEntity.toDomain() = AgentMessage(
    messageId = messageId,
    turnId = turnId,
    role = MessageRole.valueOf(role),
    content = content,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    sequence = sequence,
)

private fun SessionEntity.toDomain(
    messages: List<AgentMessage>,
    journal: List<JournalEntry>,
) = AgentSession(
    sessionId = sessionId,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    messages = messages,
    journal = journal,
)

private fun JournalEvent.toEntity(
    entryId: String,
    sessionId: String,
    turnId: String,
    sequence: Long,
    occurredAt: Instant,
): JournalEntryEntity {
    val base = JournalEntryEntity(
        entryId = entryId,
        sessionId = sessionId,
        turnId = turnId,
        sequence = sequence,
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        eventType = when (this) {
            is JournalEvent.UserRequestRecorded -> StoredJournalEventType.USER_REQUEST_RECORDED
            is JournalEvent.ModelResponseRecorded -> StoredJournalEventType.MODEL_RESPONSE_RECORDED
            is JournalEvent.ToolRequested -> StoredJournalEventType.TOOL_REQUESTED
            is JournalEvent.ApprovalRequested -> StoredJournalEventType.APPROVAL_REQUESTED
            is JournalEvent.ApprovalAccepted -> StoredJournalEventType.APPROVAL_ACCEPTED
            is JournalEvent.ApprovalRejected -> StoredJournalEventType.APPROVAL_REJECTED
            is JournalEvent.ToolAvailabilityChecked ->
                StoredJournalEventType.TOOL_AVAILABILITY_CHECKED
            is JournalEvent.ToolExecutionStarted -> StoredJournalEventType.TOOL_EXECUTION_STARTED
            is JournalEvent.ToolExecutionCompleted -> StoredJournalEventType.TOOL_EXECUTION_COMPLETED
            is JournalEvent.ToolDispatchFailed -> StoredJournalEventType.TOOL_DISPATCH_FAILED
            is JournalEvent.MemoryCreated -> StoredJournalEventType.MEMORY_CREATED
            is JournalEvent.MemoryDuplicateReused -> StoredJournalEventType.MEMORY_DUPLICATE_REUSED
            is JournalEvent.MemoryDeleted -> StoredJournalEventType.MEMORY_DELETED
            is JournalEvent.MemoryRejected -> StoredJournalEventType.MEMORY_REJECTED
            is JournalEvent.MemoryInjected -> StoredJournalEventType.MEMORY_INJECTED
            is JournalEvent.MemoryCleared -> StoredJournalEventType.MEMORY_CLEARED
            is JournalEvent.MemoryGovernanceChanged -> StoredJournalEventType.MEMORY_GOVERNANCE_CHANGED
            is JournalEvent.ErrorRecorded -> StoredJournalEventType.ERROR_RECORDED
            is JournalEvent.CancellationRecorded -> StoredJournalEventType.CANCELLATION_RECORDED
            is JournalEvent.StateTransition -> StoredJournalEventType.STATE_TRANSITION
        }.name,
    )
    return when (this) {
        is JournalEvent.UserRequestRecorded -> base.copy(primaryId = requestId, summary = summary)
        is JournalEvent.ModelResponseRecorded -> base.copy(
            primaryId = responseId,
            summary = summary,
            listValue = toolCallIds.joinToString(LIST_SEPARATOR),
        )
        is JournalEvent.ToolRequested -> base.copy(
            primaryId = callId,
            toolId = toolId,
            summary = inputSummary,
        )
        is JournalEvent.ApprovalRequested -> base.copy(
            primaryId = approvalId,
            secondaryId = callId,
        )
        is JournalEvent.ApprovalAccepted -> base.copy(
            primaryId = approvalId,
            secondaryId = callId,
        )
        is JournalEvent.ApprovalRejected -> base.copy(
            primaryId = approvalId,
            secondaryId = callId,
        )
        is JournalEvent.ToolAvailabilityChecked -> base.copy(
            primaryId = callId,
            toolId = toolId,
            summary = status.name,
        )
        is JournalEvent.ToolExecutionStarted -> base.copy(primaryId = callId, toolId = toolId)
        is JournalEvent.ToolExecutionCompleted -> base.copy(
            primaryId = callId,
            toolId = toolId,
            summary = outputSummary,
        )
        is JournalEvent.ToolDispatchFailed -> base.copy(
            primaryId = callId,
            toolId = toolId,
            errorCode = failureCode,
        )
        is JournalEvent.MemoryCreated -> base.copy(
            primaryId = memoryId,
            secondaryId = scope.name,
            toolId = category.name,
            summary = contentLength.toString(),
        )
        is JournalEvent.MemoryDuplicateReused -> base.copy(
            primaryId = memoryId,
            secondaryId = scope.name,
            toolId = category.name,
        )
        is JournalEvent.MemoryDeleted -> base.copy(
            primaryId = memoryId,
            secondaryId = scope.name,
            toolId = category.name,
        )
        is JournalEvent.MemoryRejected -> base.copy(
            primaryId = scope.name,
            secondaryId = category.name,
            errorCode = reasonCode,
        )
        is JournalEvent.MemoryInjected -> base.copy(
            primaryId = userCount.toString(),
            secondaryId = sessionCount.toString(),
            listValue = memoryIds.joinToString(LIST_SEPARATOR),
        )
        is JournalEvent.MemoryCleared -> base.copy(
            primaryId = scope.name,
            summary = count.toString(),
        )
        is JournalEvent.MemoryGovernanceChanged -> base.copy(
            primaryId = memoryId,
            secondaryId = action,
            summary = governanceKeyHashPrefix,
            listValue = relatedMemoryIds.joinToString(LIST_SEPARATOR),
        )
        is JournalEvent.ErrorRecorded -> base.copy(
            errorCode = code,
            errorMessage = userMessage,
        )
        is JournalEvent.CancellationRecorded -> base.copy(errorCode = reasonCode)
        is JournalEvent.StateTransition -> base.copy(
            fromPhase = from.name,
            toPhase = to.name,
        )
    }
}

private fun JournalEntryEntity.toDomain() = JournalEntry(
    entryId = entryId,
    sessionId = sessionId,
    turnId = turnId,
    sequence = sequence,
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    event = when (StoredJournalEventType.valueOf(eventType)) {
        StoredJournalEventType.USER_REQUEST_RECORDED -> JournalEvent.UserRequestRecorded(
            requireNotNull(primaryId),
            summary.orEmpty(),
        )
        StoredJournalEventType.MODEL_RESPONSE_RECORDED -> JournalEvent.ModelResponseRecorded(
            responseId = requireNotNull(primaryId),
            summary = summary.orEmpty(),
            toolCallIds = listValue?.takeIf(String::isNotEmpty)?.split(LIST_SEPARATOR).orEmpty(),
        )
        StoredJournalEventType.TOOL_REQUESTED -> JournalEvent.ToolRequested(
            callId = requireNotNull(primaryId),
            toolId = requireNotNull(toolId),
            inputSummary = summary.orEmpty(),
        )
        StoredJournalEventType.APPROVAL_REQUESTED -> JournalEvent.ApprovalRequested(
            requireNotNull(primaryId),
            requireNotNull(secondaryId),
        )
        StoredJournalEventType.APPROVAL_ACCEPTED -> JournalEvent.ApprovalAccepted(
            requireNotNull(primaryId),
            requireNotNull(secondaryId),
        )
        StoredJournalEventType.APPROVAL_REJECTED -> JournalEvent.ApprovalRejected(
            requireNotNull(primaryId),
            requireNotNull(secondaryId),
        )
        StoredJournalEventType.TOOL_AVAILABILITY_CHECKED ->
            JournalEvent.ToolAvailabilityChecked(
                callId = requireNotNull(primaryId),
                toolId = requireNotNull(toolId),
                status = ToolAvailabilityStatus.valueOf(requireNotNull(summary)),
            )
        StoredJournalEventType.TOOL_EXECUTION_STARTED -> JournalEvent.ToolExecutionStarted(
            requireNotNull(primaryId),
            requireNotNull(toolId),
        )
        StoredJournalEventType.TOOL_EXECUTION_COMPLETED -> JournalEvent.ToolExecutionCompleted(
            requireNotNull(primaryId),
            requireNotNull(toolId),
            summary.orEmpty(),
        )
        StoredJournalEventType.TOOL_DISPATCH_FAILED -> JournalEvent.ToolDispatchFailed(
            callId = requireNotNull(primaryId),
            toolId = requireNotNull(toolId),
            failureCode = requireNotNull(errorCode),
        )
        StoredJournalEventType.MEMORY_CREATED -> JournalEvent.MemoryCreated(
            memoryId = requireNotNull(primaryId),
            scope = MemoryScope.valueOf(requireNotNull(secondaryId)),
            category = MemoryCategory.valueOf(requireNotNull(toolId)),
            contentLength = requireNotNull(summary).toInt(),
        )
        StoredJournalEventType.MEMORY_DUPLICATE_REUSED -> JournalEvent.MemoryDuplicateReused(
            memoryId = requireNotNull(primaryId),
            scope = MemoryScope.valueOf(requireNotNull(secondaryId)),
            category = MemoryCategory.valueOf(requireNotNull(toolId)),
        )
        StoredJournalEventType.MEMORY_DELETED -> JournalEvent.MemoryDeleted(
            memoryId = requireNotNull(primaryId),
            scope = MemoryScope.valueOf(requireNotNull(secondaryId)),
            category = MemoryCategory.valueOf(requireNotNull(toolId)),
        )
        StoredJournalEventType.MEMORY_REJECTED -> JournalEvent.MemoryRejected(
            scope = MemoryScope.valueOf(requireNotNull(primaryId)),
            category = MemoryCategory.valueOf(requireNotNull(secondaryId)),
            reasonCode = requireNotNull(errorCode),
        )
        StoredJournalEventType.MEMORY_INJECTED -> JournalEvent.MemoryInjected(
            memoryIds = listValue?.takeIf(String::isNotEmpty)?.split(LIST_SEPARATOR).orEmpty(),
            userCount = requireNotNull(primaryId).toInt(),
            sessionCount = requireNotNull(secondaryId).toInt(),
        )
        StoredJournalEventType.MEMORY_CLEARED -> JournalEvent.MemoryCleared(
            scope = MemoryScope.valueOf(requireNotNull(primaryId)),
            count = requireNotNull(summary).toInt(),
        )
        StoredJournalEventType.MEMORY_GOVERNANCE_CHANGED -> JournalEvent.MemoryGovernanceChanged(
            action = requireNotNull(secondaryId),
            memoryId = primaryId,
            relatedMemoryIds = listValue?.takeIf(String::isNotEmpty)?.split(LIST_SEPARATOR).orEmpty(),
            governanceKeyHashPrefix = summary.orEmpty(),
        )
        StoredJournalEventType.ERROR_RECORDED -> JournalEvent.ErrorRecorded(
            requireNotNull(errorCode),
            errorMessage.orEmpty(),
        )
        StoredJournalEventType.CANCELLATION_RECORDED -> JournalEvent.CancellationRecorded(
            requireNotNull(errorCode),
        )
        StoredJournalEventType.STATE_TRANSITION -> JournalEvent.StateTransition(
            RuntimePhase.valueOf(requireNotNull(fromPhase)),
            RuntimePhase.valueOf(requireNotNull(toPhase)),
        )
    },
)
