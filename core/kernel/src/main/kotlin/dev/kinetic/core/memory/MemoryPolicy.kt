package dev.kinetic.core.memory

import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.logging.EventJournal
import dev.kinetic.core.logging.JournalEvent
import java.time.Clock

data class ExplicitMemoryCommand(
    val scope: MemoryScope,
    val category: MemoryCategory,
    val content: String,
    val operation: MemoryCommandOperation = MemoryCommandOperation.CREATE,
    val governanceSubject: String? = null,
    val governanceValue: String? = null,
)

enum class MemoryCommandOperation {
    CREATE,
    UPDATE,
    FORGET,
}

object ExplicitMemoryCommandParser {
    private val sessionPattern = Regex(
        "(?is)^remember\\s+for\\s+this\\s+(?:conversation|session)\\s+(?:that|:)\\s*(.+)$",
    )
    private val userPattern = Regex("(?is)^remember\\s+(?:that|:)\\s*(.+)$")
    private val sessionUpdatePattern = Regex(
        "(?is)^(?:update|replace)\\s+for\\s+this\\s+(?:conversation|session)\\s+" +
            "(?:that\\s+)?my\\s+(.+?)\\s+(?:to|with)\\s+(.+?)[.!?]?\$",
    )
    private val updatePattern = Regex(
        "(?is)^(?:update|replace)\\s+my\\s+(.+?)\\s+(?:to|with)\\s+(.+?)[.!?]?\$",
    )
    private val forgetPattern = Regex("(?is)^forget\\s+(?:that\\s+)?my\\s+(.+?)[.!?]?\$")
    private val preferenceWords = Regex("(?i)\\b(prefer|preferred|preference|favorite|favourite)\\b")

    fun parse(input: String): ExplicitMemoryCommand? {
        val trimmed = input.trim()
        sessionUpdatePattern.matchEntire(trimmed)?.let { match ->
            return updateCommand(
                scope = MemoryScope.SESSION,
                subjectInput = match.groupValues[1],
                valueInput = match.groupValues[2],
            )
        }
        updatePattern.matchEntire(trimmed)?.let { match ->
            return updateCommand(
                scope = MemoryScope.USER,
                subjectInput = match.groupValues[1],
                valueInput = match.groupValues[2],
            )
        }
        forgetPattern.matchEntire(trimmed)?.let { match ->
            val subject = normalizeGovernancePart(match.groupValues[1])
            return ExplicitMemoryCommand(
                scope = MemoryScope.USER,
                category = categoryFor(subject),
                content = subject,
                operation = MemoryCommandOperation.FORGET,
                governanceSubject = subject,
            )
        }
        sessionPattern.matchEntire(trimmed)?.let { match ->
            val content = match.groupValues[1].trim()
            val governed = parseGovernedMemoryContent(content)
            return ExplicitMemoryCommand(
                scope = MemoryScope.SESSION,
                category = MemoryCategory.TASK_CONTEXT,
                content = content,
                governanceSubject = governed?.subject,
                governanceValue = governed?.value,
            )
        }
        userPattern.matchEntire(trimmed)?.let { match ->
            val content = match.groupValues[1].trim()
            val governed = parseGovernedMemoryContent(content)
            return ExplicitMemoryCommand(
                scope = MemoryScope.USER,
                category = categoryFor(content),
                content = content,
                governanceSubject = governed?.subject,
                governanceValue = governed?.value,
            )
        }
        return null
    }

    private fun updateCommand(
        scope: MemoryScope,
        subjectInput: String,
        valueInput: String,
    ): ExplicitMemoryCommand {
        val subject = normalizeGovernancePart(subjectInput)
        val value = normalizeGovernancePart(valueInput)
        return ExplicitMemoryCommand(
            scope = scope,
            category = if (scope == MemoryScope.SESSION) MemoryCategory.TASK_CONTEXT else categoryFor(subject),
            content = "my $subject is $value",
            operation = MemoryCommandOperation.UPDATE,
            governanceSubject = subject,
            governanceValue = value,
        )
    }

    private fun categoryFor(value: String) = if (preferenceWords.containsMatchIn(value)) {
        MemoryCategory.PREFERENCE
    } else {
        MemoryCategory.FACT
    }

}

data class GovernedMemoryContent(
    val subject: String,
    val value: String,
)

private val governedMemoryContentPattern = Regex("(?is)^my\\s+(.+?)\\s+is\\s+(.+?)[.!?]?\$")

fun parseGovernedMemoryContent(content: String): GovernedMemoryContent? {
    val match = governedMemoryContentPattern.matchEntire(content.trim()) ?: return null
    val subject = normalizeGovernancePart(match.groupValues[1])
    val value = normalizeGovernancePart(match.groupValues[2])
    return GovernedMemoryContent(subject, value).takeIf { subject.isNotBlank() && value.isNotBlank() }
}

private fun normalizeGovernancePart(value: String): String = value.trim()
    .trimEnd('.', '!', '?')
    .replace(Regex("\\s+"), " ")

sealed interface MemoryPolicyDecision {
    data class Allow(val normalizedContent: String) : MemoryPolicyDecision
    data class Reject(val reasonCode: String, val userMessage: String) : MemoryPolicyDecision
}

class DeterministicMemoryPolicy {
    fun evaluate(command: ExplicitMemoryCommand): MemoryPolicyDecision {
        val normalized = command.content.trim().replace(Regex("[\\t\\r\\n]+"), " ")
        if (normalized.isBlank()) {
            return MemoryPolicyDecision.Reject(
                "memory_empty",
                "Kinetic needs non-empty content to remember.",
            )
        }
        if (normalized.length > MAX_MEMORY_CONTENT_LENGTH) {
            return MemoryPolicyDecision.Reject(
                "memory_too_large",
                "That memory is too long. Keep it under $MAX_MEMORY_CONTENT_LENGTH characters.",
            )
        }
        if (normalized.any { it == '\u0000' || (it.isISOControl() && it != ' ') }) {
            return MemoryPolicyDecision.Reject(
                "memory_invalid_content",
                "That memory contains unsupported control characters.",
            )
        }
        if (SensitiveMemoryFilter.isSensitive(normalized)) {
            return MemoryPolicyDecision.Reject(
                "memory_sensitive_content",
                "Kinetic did not store that because it looks like authentication or credential data.",
            )
        }
        return MemoryPolicyDecision.Allow(normalized)
    }
}

object SensitiveMemoryFilter {
    private val privateKey = Regex("(?i)-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----")
    private val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{8,}={0,2}")
    private val jwt = Regex("\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b")
    private val knownToken = Regex(
        "(?i)\\b(?:sk-(?:or-v1-)?[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{12,}|AIza[A-Za-z0-9_-]{20,})\\b",
    )
    private val namedSecret = Regex(
        "(?i)\\b(?:api[ _-]?key|password|passcode|pin|access[ _-]?token|refresh[ _-]?token|" +
            "auth(?:entication)?[ _-]?token|recovery[ _-]?code|private[ _-]?key|client[ _-]?secret)" +
            "\\b\\s*(?:is|:|=)\\s*\\S+",
    )

    fun isSensitive(value: String): Boolean =
        privateKey.containsMatchIn(value) ||
            bearer.containsMatchIn(value) ||
            jwt.containsMatchIn(value) ||
            knownToken.containsMatchIn(value) ||
            namedSecret.containsMatchIn(value)
}

data class MemorySource(
    val sessionId: String,
    val turnId: String,
    val messageId: String,
)

sealed interface RememberResult {
    data class Created(val record: MemoryRecord) : RememberResult
    data class Duplicate(val record: MemoryRecord) : RememberResult
    data class Replaced(val record: MemoryRecord, val supersededCount: Int) : RememberResult
    data class Conflict(val record: MemoryRecord, val conflictingCount: Int) : RememberResult
    data class Forgotten(val record: MemoryRecord) : RememberResult
    data object NotFound : RememberResult
    data class Ambiguous(val candidateCount: Int) : RememberResult
    data class Rejected(val reasonCode: String, val userMessage: String) : RememberResult
}

/** Kinetic-owned write/delete authority. Raw model output never calls this service. */
class ControlledMemoryService(
    private val repository: MemoryRepository,
    private val journal: EventJournal,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val policy: DeterministicMemoryPolicy = DeterministicMemoryPolicy(),
) {
    suspend fun remember(command: ExplicitMemoryCommand, source: MemorySource): RememberResult {
        if (command.operation == MemoryCommandOperation.FORGET) {
            val subject = requireNotNull(command.governanceSubject)
            val key = memoryGovernanceKey(command.scope, null, subject)
            journal.record(
                source.sessionId,
                source.turnId,
                JournalEvent.MemoryGovernanceChanged("FORGET_REQUESTED", null, emptyList(), key.take(12)),
            )
            return when (val result = repository.forget(key, null)) {
                is MemoryForgetResult.Forgotten -> {
                    journal.record(
                        source.sessionId,
                        source.turnId,
                        JournalEvent.MemoryGovernanceChanged(
                            "FORGOTTEN",
                            result.record.memoryId,
                            emptyList(),
                            key.take(12),
                        ),
                    )
                    RememberResult.Forgotten(result.record)
                }
                MemoryForgetResult.NotFound -> {
                    journal.record(
                        source.sessionId,
                        source.turnId,
                        JournalEvent.MemoryGovernanceChanged(
                            "FORGET_REFUSED",
                            null,
                            emptyList(),
                            key.take(12),
                        ),
                    )
                    RememberResult.NotFound
                }
                is MemoryForgetResult.Ambiguous -> {
                    journal.record(
                        source.sessionId,
                        source.turnId,
                        JournalEvent.MemoryGovernanceChanged(
                            "FORGET_REFUSED",
                            null,
                            result.candidates.take(8).map(MemoryRecord::memoryId),
                            key.take(12),
                        ),
                    )
                    RememberResult.Ambiguous(result.candidates.size)
                }
            }
        }
        val decision = policy.evaluate(command)
        if (decision is MemoryPolicyDecision.Reject) {
            journal.record(
                source.sessionId,
                source.turnId,
                JournalEvent.MemoryRejected(command.scope, command.category, decision.reasonCode),
            )
            return RememberResult.Rejected(decision.reasonCode, decision.userMessage)
        }
        val content = (decision as MemoryPolicyDecision.Allow).normalizedContent
        val now = clock.instant()
        val ownerSessionId = source.sessionId.takeIf { command.scope == MemoryScope.SESSION }
        val governanceKey = command.governanceSubject?.let {
            memoryGovernanceKey(command.scope, ownerSessionId, it)
        }
        val record = MemoryRecord(
            memoryId = ids.nextId("memory"),
            scope = command.scope,
            category = command.category,
            content = content,
            provenance = MemoryProvenance.USER_EXPLICIT,
            ownerSessionId = ownerSessionId,
            sourceSessionId = source.sessionId,
            sourceTurnId = source.turnId,
            sourceMessageId = source.messageId,
            createdAt = now,
            updatedAt = now,
            governanceKey = governanceKey,
            governanceSubject = command.governanceSubject,
            governanceValue = command.governanceValue,
        )
        return when (
            val result = repository.govern(
                record,
                if (command.operation == MemoryCommandOperation.UPDATE) {
                    MemoryGovernanceWriteMode.REPLACE
                } else {
                    MemoryGovernanceWriteMode.CREATE
                },
            )
        ) {
            is MemoryGovernanceWriteResult.Created -> {
                journal.record(
                    source.sessionId,
                    source.turnId,
                    JournalEvent.MemoryCreated(
                        result.record.memoryId,
                        result.record.scope,
                        result.record.category,
                        result.record.content.length,
                    ),
                )
                RememberResult.Created(result.record)
            }
            is MemoryGovernanceWriteResult.Duplicate -> {
                journal.record(
                    source.sessionId,
                    source.turnId,
                    JournalEvent.MemoryDuplicateReused(
                        result.record.memoryId,
                        result.record.scope,
                        result.record.category,
                    ),
                )
                RememberResult.Duplicate(result.record)
            }
            is MemoryGovernanceWriteResult.Replaced -> {
                journal.record(
                    source.sessionId,
                    source.turnId,
                    JournalEvent.MemoryGovernanceChanged(
                        "UPDATED",
                        result.record.memoryId,
                        result.superseded.take(8).map(MemoryRecord::memoryId),
                        result.record.governanceKey.orEmpty().take(12),
                    ),
                )
                RememberResult.Replaced(result.record, result.superseded.size)
            }
            is MemoryGovernanceWriteResult.Conflict -> {
                journal.record(
                    source.sessionId,
                    source.turnId,
                    JournalEvent.MemoryGovernanceChanged(
                        "CONFLICT_DETECTED",
                        result.record.memoryId,
                        result.conflicting.take(8).map(MemoryRecord::memoryId),
                        result.record.governanceKey.orEmpty().take(12),
                    ),
                )
                RememberResult.Conflict(result.record, result.conflicting.size)
            }
        }
    }

    suspend fun replace(
        memoryId: String,
        newValue: String,
        source: MemorySource,
    ): RememberResult {
        val existing = repository.get(memoryId) ?: return RememberResult.NotFound
        val subject = existing.governanceSubject ?: return RememberResult.Rejected(
            "memory_not_governed",
            "This memory has no deterministic subject. Delete it and create a governed memory instead.",
        )
        return remember(
            ExplicitMemoryCommand(
                scope = existing.scope,
                category = existing.category,
                content = "my $subject is ${newValue.trim()}",
                operation = MemoryCommandOperation.UPDATE,
                governanceSubject = subject,
                governanceValue = newValue.trim(),
            ),
            source,
        )
    }

    suspend fun resolve(memoryId: String, source: MemorySource): MemoryResolutionResult {
        val result = repository.resolve(memoryId, clock.instant())
        if (result is MemoryResolutionResult.Resolved) {
            journal.record(
                source.sessionId,
                source.turnId,
                JournalEvent.MemoryGovernanceChanged(
                    "RESOLVED",
                    result.selected.memoryId,
                    result.superseded.take(8).map(MemoryRecord::memoryId),
                    result.selected.governanceKey.orEmpty().take(12),
                ),
            )
        }
        return result
    }

    suspend fun delete(memoryId: String, sessionId: String, operationId: String): Boolean {
        val record = repository.get(memoryId) ?: return false
        if (!repository.delete(memoryId)) return false
        journal.record(
            sessionId,
            operationId,
            JournalEvent.MemoryDeleted(record.memoryId, record.scope, record.category),
        )
        return true
    }

    suspend fun clearSession(sessionId: String, operationId: String): Int {
        val count = repository.clearSession(sessionId)
        journal.record(
            sessionId,
            operationId,
            JournalEvent.MemoryCleared(MemoryScope.SESSION, count),
        )
        return count
    }

    suspend fun clearUser(sessionId: String, operationId: String): Int {
        val count = repository.clearUser()
        journal.record(
            sessionId,
            operationId,
            JournalEvent.MemoryCleared(MemoryScope.USER, count),
        )
        return count
    }
}
