package dev.kinetic.data.persistence

import androidx.room.withTransaction
import dev.kinetic.core.memory.MAX_SESSION_MEMORY_CANDIDATES
import dev.kinetic.core.memory.MAX_USER_MEMORY_CANDIDATES
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryContext
import dev.kinetic.core.memory.MemoryCreateResult
import dev.kinetic.core.memory.MemoryForgetResult
import dev.kinetic.core.memory.MemoryGovernanceWriteMode
import dev.kinetic.core.memory.MemoryGovernanceWriteResult
import dev.kinetic.core.memory.MemoryProvenance
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryRepository
import dev.kinetic.core.memory.MemoryResolutionResult
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import dev.kinetic.core.memory.memoryGovernanceKey
import dev.kinetic.core.memory.parseGovernedMemoryContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class RoomMemoryRepository(
    private val database: KineticDatabase,
) : MemoryRepository {
    private val dao = database.kineticDao()

    override suspend fun create(record: MemoryRecord): MemoryCreateResult =
        database.withTransaction {
            val existing = dao.memoryByDeduplicationKey(record.deduplicationKey)
            if (existing == null) {
                check(dao.insertMemory(record.toEntity()) != -1L)
                MemoryCreateResult.Created(record)
            } else {
                MemoryCreateResult.Duplicate(existing.toDomain())
            }
        }

    override suspend fun govern(
        record: MemoryRecord,
        mode: MemoryGovernanceWriteMode,
    ): MemoryGovernanceWriteResult = database.withTransaction {
        if (record.governanceKey == null) {
            val duplicate = dao.memoryByDeduplicationKey(record.deduplicationKey)?.toDomain()
            if (duplicate != null) return@withTransaction MemoryGovernanceWriteResult.Duplicate(duplicate)
            check(dao.insertMemory(record.toEntity()) != -1L)
            return@withTransaction MemoryGovernanceWriteResult.Created(record)
        }
        val governanceKey = requireNotNull(record.governanceKey)
        val governedCurrent = dao.currentGovernedMemories(governanceKey, record.ownerSessionId)
            .map(MemoryEntity::toDomain)
        val adoptedLegacy = dao.currentLegacyMemories(record.scope.name, record.ownerSessionId)
            .map(MemoryEntity::toDomain)
            .mapNotNull { it.adoptGovernanceFor(record) }
        adoptedLegacy.forEach { dao.updateMemory(it.toEntity()) }
        val current = (governedCurrent + adoptedLegacy).sortedCurrent()
        if (record.provenance != MemoryProvenance.USER_EXPLICIT &&
            current.any { it.provenance == MemoryProvenance.USER_EXPLICIT }
        ) return@withTransaction MemoryGovernanceWriteResult.Duplicate(
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
                    ).also { dao.updateMemory(it.toEntity()) }
                }
                dao.updateMemory(replacement.toEntity())
                return@withTransaction MemoryGovernanceWriteResult.Replaced(replacement, superseded)
            }
            return@withTransaction MemoryGovernanceWriteResult.Duplicate(duplicate)
        }
        if (current.isEmpty()) {
            check(dao.insertMemory(record.toEntity()) != -1L)
            return@withTransaction MemoryGovernanceWriteResult.Created(record)
        }
        val explicitOverride = record.provenance == MemoryProvenance.USER_EXPLICIT &&
            current.none { it.provenance == MemoryProvenance.USER_EXPLICIT }
        if (record.provenance != MemoryProvenance.USER_EXPLICIT &&
            current.any { it.provenance == MemoryProvenance.USER_EXPLICIT }
        ) {
            return@withTransaction MemoryGovernanceWriteResult.Duplicate(
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
                ).also { dao.updateMemory(it.toEntity()) }
            }
            check(dao.insertMemory(replacement.toEntity()) != -1L)
            return@withTransaction MemoryGovernanceWriteResult.Replaced(replacement, superseded)
        }
        val conflicted = current.map { existing ->
            existing.copy(
                retentionState = MemoryRetentionState.CONFLICTED,
                updatedAt = record.updatedAt,
            ).also { dao.updateMemory(it.toEntity()) }
        }
        val newConflict = record.copy(retentionState = MemoryRetentionState.CONFLICTED)
        check(dao.insertMemory(newConflict.toEntity()) != -1L)
        MemoryGovernanceWriteResult.Conflict(newConflict, conflicted)
    }

    override suspend fun resolve(
        memoryId: String,
        updatedAt: Instant,
    ): MemoryResolutionResult = database.withTransaction {
        val selected = dao.memory(memoryId)?.toDomain() ?: return@withTransaction MemoryResolutionResult.NotFound
        val governanceKey = selected.governanceKey
        if (selected.retentionState != MemoryRetentionState.CONFLICTED || governanceKey == null) {
            return@withTransaction MemoryResolutionResult.NotConflicted
        }
        val current = dao.currentGovernedMemories(governanceKey, selected.ownerSessionId)
            .map(MemoryEntity::toDomain)
        val resolved = selected.copy(
            retentionState = MemoryRetentionState.ACTIVE,
            updatedAt = updatedAt,
            supersededByMemoryId = null,
        )
        val superseded = current.filterNot { it.memoryId == memoryId }.map { existing ->
            existing.copy(
                retentionState = MemoryRetentionState.SUPERSEDED,
                updatedAt = updatedAt,
                supersededByMemoryId = memoryId,
            ).also { dao.updateMemory(it.toEntity()) }
        }
        dao.updateMemory(resolved.toEntity())
        MemoryResolutionResult.Resolved(resolved, superseded)
    }

    override suspend fun forget(
        governanceKey: String,
        ownerSessionId: String?,
    ): MemoryForgetResult = database.withTransaction {
        val current = dao.currentGovernedMemories(governanceKey, ownerSessionId).map(MemoryEntity::toDomain)
        when (current.size) {
            0 -> MemoryForgetResult.NotFound
            1 -> {
                val target = current.single()
                check(dao.deleteMemory(target.memoryId) == 1)
                MemoryForgetResult.Forgotten(target)
            }
            else -> MemoryForgetResult.Ambiguous(current)
        }
    }

    override suspend fun get(memoryId: String): MemoryRecord? = dao.memory(memoryId)?.toDomain()

    override fun observeUserMemories(): Flow<List<MemoryRecord>> =
        dao.observeUserMemories().map { records -> records.map(MemoryEntity::toDomain) }

    override fun observeSessionMemories(sessionId: String): Flow<List<MemoryRecord>> =
        dao.observeSessionMemories(sessionId).map { records -> records.map(MemoryEntity::toDomain) }

    override suspend fun candidates(
        sessionId: String,
        userLimit: Int,
        sessionLimit: Int,
    ): MemoryContext {
        require(userLimit in 0..MAX_USER_MEMORY_CANDIDATES)
        require(sessionLimit in 0..MAX_SESSION_MEMORY_CANDIDATES)
        return database.withTransaction {
            MemoryContext(
                userMemories = dao.userMemoriesForContext(userLimit).map(MemoryEntity::toDomain),
                sessionMemories = dao.sessionMemoriesForContext(sessionId, sessionLimit)
                    .map(MemoryEntity::toDomain),
            )
        }
    }

    override suspend fun delete(memoryId: String): Boolean = dao.deleteMemory(memoryId) == 1

    override suspend fun clearSession(sessionId: String): Int = dao.clearSessionMemories(sessionId)

    override suspend fun clearUser(): Int = dao.clearUserMemories()
}

private fun MemoryRecord.toEntity() = MemoryEntity(
    memoryId = memoryId,
    scope = scope.name,
    category = category.name,
    content = content,
    provenance = provenance.name,
    ownerSessionId = ownerSessionId,
    sourceSessionId = sourceSessionId,
    sourceTurnId = sourceTurnId,
    sourceMessageId = sourceMessageId,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    userVisible = userVisible,
    retentionState = retentionState.name,
    deduplicationKey = deduplicationKey,
    governanceKey = governanceKey,
    governanceSubject = governanceSubject,
    governanceValue = governanceValue,
    supersedesMemoryId = supersedesMemoryId,
    supersededByMemoryId = supersededByMemoryId,
)

private fun MemoryEntity.toDomain() = MemoryRecord(
    memoryId = memoryId,
    scope = MemoryScope.valueOf(scope),
    category = MemoryCategory.valueOf(category),
    content = content,
    provenance = MemoryProvenance.valueOf(provenance),
    ownerSessionId = ownerSessionId,
    sourceSessionId = sourceSessionId,
    sourceTurnId = sourceTurnId,
    sourceMessageId = sourceMessageId,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    userVisible = userVisible,
    retentionState = MemoryRetentionState.valueOf(retentionState),
    deduplicationKey = deduplicationKey,
    governanceKey = governanceKey,
    governanceSubject = governanceSubject,
    governanceValue = governanceValue,
    supersedesMemoryId = supersedesMemoryId,
    supersededByMemoryId = supersededByMemoryId,
)

private fun MemoryRecord.adoptGovernanceFor(target: MemoryRecord): MemoryRecord? {
    if (target.provenance != MemoryProvenance.USER_EXPLICIT) return null
    val targetKey = target.governanceKey ?: return null
    val parsed = parseGovernedMemoryContent(content) ?: return null
    val parsedKey = memoryGovernanceKey(scope, ownerSessionId, parsed.subject)
    if (parsedKey != targetKey) return null
    return copy(
        governanceKey = parsedKey,
        governanceSubject = parsed.subject,
        governanceValue = parsed.value,
    )
}

private fun Collection<MemoryRecord>.sortedCurrent(): List<MemoryRecord> =
    sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
