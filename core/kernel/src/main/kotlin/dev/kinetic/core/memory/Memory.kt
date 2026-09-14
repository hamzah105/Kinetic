package dev.kinetic.core.memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

const val MAX_MEMORY_CONTENT_LENGTH = 1_000
const val MAX_USER_MEMORIES_IN_CONTEXT = 4
const val MAX_SESSION_MEMORIES_IN_CONTEXT = 4
const val MAX_USER_MEMORY_CANDIDATES = 32
const val MAX_SESSION_MEMORY_CANDIDATES = 32
const val MAX_MEMORY_UI_PREVIEW_LENGTH = 240

enum class MemoryScope {
    SESSION,
    USER,
}

enum class MemoryCategory {
    FACT,
    PREFERENCE,
    TASK_CONTEXT,
}

enum class MemoryProvenance {
    USER_EXPLICIT,
    USER_MESSAGE_DERIVED,
    SYSTEM_CREATED,
}

enum class MemoryRetentionState {
    ACTIVE,
    SUPERSEDED,
    CONFLICTED,
}

data class MemoryRecord(
    val memoryId: String,
    val scope: MemoryScope,
    val category: MemoryCategory,
    val content: String,
    val provenance: MemoryProvenance,
    val ownerSessionId: String?,
    val sourceSessionId: String,
    val sourceTurnId: String,
    val sourceMessageId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val userVisible: Boolean = true,
    val retentionState: MemoryRetentionState = MemoryRetentionState.ACTIVE,
    val governanceKey: String? = null,
    val governanceSubject: String? = null,
    val governanceValue: String? = null,
    val supersedesMemoryId: String? = null,
    val supersededByMemoryId: String? = null,
    val deduplicationKey: String = memoryDeduplicationKey(
        scope = scope,
        category = category,
        content = content,
        ownerSessionId = ownerSessionId,
    ),
) {
    init {
        require(memoryId.isNotBlank() && memoryId.length <= 200)
        require(content.isNotBlank() && content.length <= MAX_MEMORY_CONTENT_LENGTH)
        require(content.none { it == '\u0000' })
        require(sourceSessionId.isNotBlank())
        require(sourceTurnId.isNotBlank())
        require(sourceMessageId.isNotBlank())
        require(updatedAt >= createdAt)
        require(governanceKey == null || governanceKey.length == 64)
        require((governanceSubject == null) == (governanceKey == null))
        require((governanceValue == null) == (governanceKey == null))
        require(
            when (scope) {
                MemoryScope.SESSION -> !ownerSessionId.isNullOrBlank()
                MemoryScope.USER -> ownerSessionId == null
            },
        )
        require(deduplicationKey == memoryDeduplicationKey(scope, category, content, ownerSessionId))
    }
}

data class MemoryContext(
    val userMemories: List<MemoryRecord> = emptyList(),
    val sessionMemories: List<MemoryRecord> = emptyList(),
) {
    val ordered: List<MemoryRecord> get() = userMemories + sessionMemories
}

sealed interface MemoryCreateResult {
    val record: MemoryRecord

    data class Created(override val record: MemoryRecord) : MemoryCreateResult
    data class Duplicate(override val record: MemoryRecord) : MemoryCreateResult
}

enum class MemoryGovernanceWriteMode {
    CREATE,
    REPLACE,
}

sealed interface MemoryGovernanceWriteResult {
    val record: MemoryRecord

    data class Created(override val record: MemoryRecord) : MemoryGovernanceWriteResult
    data class Duplicate(override val record: MemoryRecord) : MemoryGovernanceWriteResult
    data class Replaced(
        override val record: MemoryRecord,
        val superseded: List<MemoryRecord>,
    ) : MemoryGovernanceWriteResult
    data class Conflict(
        override val record: MemoryRecord,
        val conflicting: List<MemoryRecord>,
    ) : MemoryGovernanceWriteResult
}

sealed interface MemoryResolutionResult {
    data class Resolved(val selected: MemoryRecord, val superseded: List<MemoryRecord>) : MemoryResolutionResult
    data object NotFound : MemoryResolutionResult
    data object NotConflicted : MemoryResolutionResult
}

sealed interface MemoryForgetResult {
    data class Forgotten(val record: MemoryRecord) : MemoryForgetResult
    data object NotFound : MemoryForgetResult
    data class Ambiguous(val candidates: List<MemoryRecord>) : MemoryForgetResult
}

/** Pure Kotlin persistence port. Android production uses the existing Room database. */
interface MemoryRepository {
    suspend fun create(record: MemoryRecord): MemoryCreateResult
    suspend fun govern(
        record: MemoryRecord,
        mode: MemoryGovernanceWriteMode,
    ): MemoryGovernanceWriteResult
    suspend fun resolve(memoryId: String, updatedAt: Instant): MemoryResolutionResult
    suspend fun forget(governanceKey: String, ownerSessionId: String?): MemoryForgetResult
    suspend fun get(memoryId: String): MemoryRecord?
    fun observeUserMemories(): Flow<List<MemoryRecord>>
    fun observeSessionMemories(sessionId: String): Flow<List<MemoryRecord>>
    suspend fun candidates(
        sessionId: String,
        userLimit: Int = MAX_USER_MEMORY_CANDIDATES,
        sessionLimit: Int = MAX_SESSION_MEMORY_CANDIDATES,
    ): MemoryContext
    suspend fun context(
        sessionId: String,
        userLimit: Int = MAX_USER_MEMORIES_IN_CONTEXT,
        sessionLimit: Int = MAX_SESSION_MEMORIES_IN_CONTEXT,
    ): MemoryContext {
        require(userLimit in 0..MAX_USER_MEMORIES_IN_CONTEXT)
        require(sessionLimit in 0..MAX_SESSION_MEMORIES_IN_CONTEXT)
        return candidates(sessionId, userLimit, sessionLimit)
    }
    suspend fun delete(memoryId: String): Boolean
    suspend fun clearSession(sessionId: String): Int
    suspend fun clearUser(): Int
}

class InMemoryMemoryRepository : MemoryRepository {
    private val mutex = Mutex()
    private val records = MutableStateFlow<Map<String, MemoryRecord>>(emptyMap())

    override suspend fun create(record: MemoryRecord): MemoryCreateResult = mutex.withLock {
        val duplicate = records.value.values.firstOrNull {
            it.deduplicationKey == record.deduplicationKey
        }
        if (duplicate != null) return@withLock MemoryCreateResult.Duplicate(duplicate)
        records.value = records.value + (record.memoryId to record)
        MemoryCreateResult.Created(record)
    }

    override suspend fun govern(
        record: MemoryRecord,
        mode: MemoryGovernanceWriteMode,
    ): MemoryGovernanceWriteResult = mutex.withLock {
        if (record.governanceKey == null) {
            records.value.values.firstOrNull { it.deduplicationKey == record.deduplicationKey }?.let {
                return@withLock MemoryGovernanceWriteResult.Duplicate(it)
            }
            records.value = records.value + (record.memoryId to record)
            return@withLock MemoryGovernanceWriteResult.Created(record)
        }
        val governedCurrent = records.value.values.currentFor(record.governanceKey, record.ownerSessionId)
        val adoptedLegacy = records.value.values.adoptableLegacyFor(record)
        if (adoptedLegacy.isNotEmpty()) {
            records.value = records.value + adoptedLegacy.associateBy(MemoryRecord::memoryId)
        }
        val current = (governedCurrent + adoptedLegacy).sortedCurrent()
        if (record.provenance != MemoryProvenance.USER_EXPLICIT &&
            current.any { it.provenance == MemoryProvenance.USER_EXPLICIT }
        ) return@withLock MemoryGovernanceWriteResult.Duplicate(
            current.first { it.provenance == MemoryProvenance.USER_EXPLICIT },
        )
        val duplicate = current.firstOrNull { it.deduplicationKey == record.deduplicationKey }
        if (duplicate != null) {
            if (mode == MemoryGovernanceWriteMode.REPLACE && current.any { it.memoryId != duplicate.memoryId }) {
                val replacement = duplicate.copy(
                    retentionState = MemoryRetentionState.ACTIVE,
                    updatedAt = record.updatedAt,
                    supersededByMemoryId = null,
                )
                val superseded = current.filterNot { it.memoryId == replacement.memoryId }.map { existing ->
                    existing.copy(
                        retentionState = MemoryRetentionState.SUPERSEDED,
                        updatedAt = record.updatedAt,
                        supersededByMemoryId = replacement.memoryId,
                    )
                }
                records.value = records.value + superseded.associateBy(MemoryRecord::memoryId) +
                    (replacement.memoryId to replacement)
                return@withLock MemoryGovernanceWriteResult.Replaced(replacement, superseded)
            }
            return@withLock MemoryGovernanceWriteResult.Duplicate(duplicate)
        }
        if (current.isEmpty()) {
            records.value = records.value + (record.memoryId to record)
            return@withLock MemoryGovernanceWriteResult.Created(record)
        }
        val explicitOverride = record.provenance == MemoryProvenance.USER_EXPLICIT &&
            current.none { it.provenance == MemoryProvenance.USER_EXPLICIT }
        if (record.provenance != MemoryProvenance.USER_EXPLICIT &&
            current.any { it.provenance == MemoryProvenance.USER_EXPLICIT }
        ) {
            return@withLock MemoryGovernanceWriteResult.Duplicate(
                current.first { it.provenance == MemoryProvenance.USER_EXPLICIT },
            )
        }
        if (mode == MemoryGovernanceWriteMode.REPLACE || explicitOverride) {
            val target = current.maxWithOrNull(compareBy<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
            val replacement = record.copy(supersedesMemoryId = target?.memoryId)
            val superseded = current.map { existing ->
                existing.copy(
                    retentionState = MemoryRetentionState.SUPERSEDED,
                    updatedAt = record.updatedAt,
                    supersededByMemoryId = replacement.memoryId,
                )
            }
            records.value = records.value + superseded.associateBy(MemoryRecord::memoryId) +
                (replacement.memoryId to replacement)
            return@withLock MemoryGovernanceWriteResult.Replaced(replacement, superseded)
        }
        val conflictedCurrent = current.map { existing ->
            existing.copy(retentionState = MemoryRetentionState.CONFLICTED, updatedAt = record.updatedAt)
        }
        val conflictedNew = record.copy(retentionState = MemoryRetentionState.CONFLICTED)
        records.value = records.value + conflictedCurrent.associateBy(MemoryRecord::memoryId) +
            (conflictedNew.memoryId to conflictedNew)
        MemoryGovernanceWriteResult.Conflict(conflictedNew, conflictedCurrent)
    }

    override suspend fun resolve(memoryId: String, updatedAt: Instant): MemoryResolutionResult = mutex.withLock {
        val selected = records.value[memoryId] ?: return@withLock MemoryResolutionResult.NotFound
        if (selected.retentionState != MemoryRetentionState.CONFLICTED || selected.governanceKey == null) {
            return@withLock MemoryResolutionResult.NotConflicted
        }
        val group = records.value.values.currentFor(selected.governanceKey, selected.ownerSessionId)
        val resolved = selected.copy(
            retentionState = MemoryRetentionState.ACTIVE,
            updatedAt = updatedAt,
            supersededByMemoryId = null,
        )
        val superseded = group.filterNot { it.memoryId == memoryId }.map {
            it.copy(
                retentionState = MemoryRetentionState.SUPERSEDED,
                updatedAt = updatedAt,
                supersededByMemoryId = memoryId,
            )
        }
        records.value = records.value + superseded.associateBy(MemoryRecord::memoryId) +
            (memoryId to resolved)
        MemoryResolutionResult.Resolved(resolved, superseded)
    }

    override suspend fun forget(
        governanceKey: String,
        ownerSessionId: String?,
    ): MemoryForgetResult = mutex.withLock {
        val current = records.value.values.currentFor(governanceKey, ownerSessionId)
        when (current.size) {
            0 -> MemoryForgetResult.NotFound
            1 -> {
                val target = current.single()
                records.value = records.value - target.memoryId
                MemoryForgetResult.Forgotten(target)
            }
            else -> MemoryForgetResult.Ambiguous(current)
        }
    }

    override suspend fun get(memoryId: String): MemoryRecord? = records.value[memoryId]

    override fun observeUserMemories(): Flow<List<MemoryRecord>> = records
        .map { values -> values.values.userMemoryHistory() }
        .distinctUntilChanged()

    override fun observeSessionMemories(sessionId: String): Flow<List<MemoryRecord>> = records
        .map { values -> values.values.sessionMemoryHistory(sessionId) }
        .distinctUntilChanged()

    override suspend fun candidates(
        sessionId: String,
        userLimit: Int,
        sessionLimit: Int,
    ): MemoryContext {
        require(userLimit in 0..MAX_USER_MEMORY_CANDIDATES)
        require(sessionLimit in 0..MAX_SESSION_MEMORY_CANDIDATES)
        val snapshot = records.value.values
        return MemoryContext(
            userMemories = snapshot.userMemories().take(userLimit),
            sessionMemories = snapshot.sessionMemories(sessionId).take(sessionLimit),
        )
    }

    override suspend fun delete(memoryId: String): Boolean = mutex.withLock {
        if (memoryId !in records.value) return@withLock false
        records.value = records.value - memoryId
        true
    }

    override suspend fun clearSession(sessionId: String): Int = mutex.withLock {
        val ids = records.value.values
            .filter { it.scope == MemoryScope.SESSION && it.ownerSessionId == sessionId }
            .mapTo(mutableSetOf(), MemoryRecord::memoryId)
        records.value = records.value - ids
        ids.size
    }

    override suspend fun clearUser(): Int = mutex.withLock {
        val ids = records.value.values
            .filter { it.scope == MemoryScope.USER }
            .mapTo(mutableSetOf(), MemoryRecord::memoryId)
        records.value = records.value - ids
        ids.size
    }
}

fun memoryDeduplicationKey(
    scope: MemoryScope,
    category: MemoryCategory,
    content: String,
    ownerSessionId: String?,
): String {
    val normalized = content.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
    val owner = if (scope == MemoryScope.SESSION) ownerSessionId.orEmpty() else "USER"
    val canonical = listOf(scope.name, owner, category.name, normalized).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

fun memoryGovernanceKey(scope: MemoryScope, ownerSessionId: String?, subject: String): String {
    val normalized = subject.trim().lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
    val owner = if (scope == MemoryScope.SESSION) ownerSessionId.orEmpty() else "USER"
    val canonical = listOf(scope.name, owner, normalized).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun Collection<MemoryRecord>.userMemories(): List<MemoryRecord> = asSequence()
    .filter {
        it.scope == MemoryScope.USER &&
            it.userVisible &&
            it.retentionState != MemoryRetentionState.SUPERSEDED
    }
    .sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
    .toList()

private fun Collection<MemoryRecord>.sessionMemories(sessionId: String): List<MemoryRecord> =
    asSequence()
        .filter {
            it.scope == MemoryScope.SESSION &&
                it.ownerSessionId == sessionId &&
                it.userVisible &&
                it.retentionState != MemoryRetentionState.SUPERSEDED
        }
        .sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
    .toList()

private fun Collection<MemoryRecord>.userMemoryHistory(): List<MemoryRecord> = asSequence()
    .filter { it.scope == MemoryScope.USER && it.userVisible }
    .sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
    .toList()

private fun Collection<MemoryRecord>.sessionMemoryHistory(sessionId: String): List<MemoryRecord> =
    asSequence()
        .filter {
            it.scope == MemoryScope.SESSION && it.ownerSessionId == sessionId && it.userVisible
        }
        .sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
        .toList()

private fun Collection<MemoryRecord>.currentFor(
    governanceKey: String?,
    ownerSessionId: String?,
): List<MemoryRecord> = if (governanceKey == null) {
    emptyList()
} else {
    filter {
        it.governanceKey == governanceKey &&
            it.ownerSessionId == ownerSessionId &&
            it.retentionState != MemoryRetentionState.SUPERSEDED
    }.sortedCurrent()
}

private fun Collection<MemoryRecord>.adoptableLegacyFor(target: MemoryRecord): List<MemoryRecord> {
    if (target.provenance != MemoryProvenance.USER_EXPLICIT) return emptyList()
    val targetKey = target.governanceKey ?: return emptyList()
    return asSequence()
        .filter {
            it.governanceKey == null &&
                it.scope == target.scope &&
                it.ownerSessionId == target.ownerSessionId &&
                it.provenance == MemoryProvenance.USER_EXPLICIT &&
                it.userVisible &&
                it.retentionState != MemoryRetentionState.SUPERSEDED
        }
        .mapNotNull { legacy ->
            val parsed = parseGovernedMemoryContent(legacy.content) ?: return@mapNotNull null
            val key = memoryGovernanceKey(legacy.scope, legacy.ownerSessionId, parsed.subject)
            legacy.takeIf { key == targetKey }?.copy(
                governanceKey = key,
                governanceSubject = parsed.subject,
                governanceValue = parsed.value,
            )
        }
        .toList()
        .sortedCurrent()
}

private fun Collection<MemoryRecord>.sortedCurrent(): List<MemoryRecord> =
    sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
