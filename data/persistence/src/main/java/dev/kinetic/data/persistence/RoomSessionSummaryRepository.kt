package dev.kinetic.data.persistence

import dev.kinetic.core.context.SessionSummary
import dev.kinetic.core.context.SessionSummaryProvenance
import dev.kinetic.core.context.SessionSummaryRepository
import dev.kinetic.core.context.SessionSummaryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class RoomSessionSummaryRepository(
    database: KineticDatabase,
) : SessionSummaryRepository {
    private val dao = database.kineticDao()

    override suspend fun latest(sessionId: String): SessionSummary? =
        dao.latestSessionSummary(sessionId)?.toDomain()

    override fun observeLatest(sessionId: String): Flow<SessionSummary?> =
        dao.observeLatestSessionSummary(sessionId).map { it?.toDomain() }

    override suspend fun save(summary: SessionSummary) {
        dao.insertSessionSummary(summary.toEntity())
    }

    override suspend fun delete(summaryId: String): Boolean =
        dao.deleteSessionSummary(summaryId) == 1
}

private fun SessionSummary.toEntity() = SessionSummaryEntity(
    summaryId = summaryId,
    sessionId = sessionId,
    content = content,
    firstCoveredMessageSequence = firstCoveredMessageSequence,
    lastCoveredMessageSequence = lastCoveredMessageSequence,
    sourceMessageCount = sourceMessageCount,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    sourceDigest = sourceDigest,
    provenance = provenance.name,
    status = status.name,
)

private fun SessionSummaryEntity.toDomain() = SessionSummary(
    summaryId = summaryId,
    sessionId = sessionId,
    content = content,
    firstCoveredMessageSequence = firstCoveredMessageSequence,
    lastCoveredMessageSequence = lastCoveredMessageSequence,
    sourceMessageCount = sourceMessageCount,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    sourceDigest = sourceDigest,
    provenance = SessionSummaryProvenance.valueOf(provenance),
    status = SessionSummaryStatus.valueOf(status),
)
