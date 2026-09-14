package dev.kinetic.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KineticDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(entity: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun session(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Update
    suspend fun updateSession(entity: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(entity: MessageEntity)

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM messages WHERE sessionId = :sessionId")
    suspend fun nextMessageSequence(sessionId: String): Long

    @Query(
        "SELECT * FROM messages WHERE sessionId = :sessionId " +
            "ORDER BY sequence ASC",
    )
    suspend fun messages(sessionId: String): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE sessionId = :sessionId " +
            "ORDER BY sequence ASC",
    )
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalEntry(entity: JournalEntryEntity)

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM journal_entries WHERE sessionId = :sessionId")
    suspend fun nextJournalSequence(sessionId: String): Long

    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY sequence ASC")
    suspend fun journalEntries(sessionId: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY sequence ASC")
    fun observeJournalEntries(sessionId: String): Flow<List<JournalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTurn(entity: TurnEntity)

    @Update
    suspend fun updateTurn(entity: TurnEntity)

    @Query("SELECT * FROM turns WHERE turnId = :turnId")
    suspend fun turn(turnId: String): TurnEntity?

    @Query(
        "SELECT * FROM turns WHERE sessionId = :sessionId " +
            "ORDER BY createdAtEpochMillis DESC, turnId DESC LIMIT 1",
    )
    suspend fun latestTurn(sessionId: String): TurnEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApproval(entity: ApprovalEntity)

    @Update
    suspend fun updateApproval(entity: ApprovalEntity)

    @Query("SELECT * FROM approvals WHERE approvalId = :approvalId")
    suspend fun approval(approvalId: String): ApprovalEntity?

    @Query("SELECT * FROM approvals WHERE turnId = :turnId")
    suspend fun approvalForTurn(turnId: String): ApprovalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEffect(entity: EffectEntity)

    @Update
    suspend fun updateEffect(entity: EffectEntity)

    @Query("SELECT * FROM effects WHERE callId = :callId")
    suspend fun effect(callId: String): EffectEntity?

    @Query("SELECT * FROM effects WHERE turnId = :turnId")
    suspend fun effectForTurn(turnId: String): EffectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemory(entity: MemoryEntity): Long

    @Query("SELECT * FROM memories WHERE memoryId = :memoryId")
    suspend fun memory(memoryId: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE deduplicationKey = :deduplicationKey")
    suspend fun memoryByDeduplicationKey(deduplicationKey: String): MemoryEntity?

    @Query(
        "SELECT * FROM memories WHERE governanceKey = :governanceKey " +
            "AND ((:ownerSessionId IS NULL AND ownerSessionId IS NULL) OR ownerSessionId = :ownerSessionId) " +
            "AND retentionState != 'SUPERSEDED' ORDER BY updatedAtEpochMillis DESC, memoryId ASC",
    )
    suspend fun currentGovernedMemories(
        governanceKey: String,
        ownerSessionId: String?,
    ): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE governanceKey IS NULL AND scope = :scope " +
            "AND ((:ownerSessionId IS NULL AND ownerSessionId IS NULL) OR ownerSessionId = :ownerSessionId) " +
            "AND provenance = 'USER_EXPLICIT' AND userVisible = 1 " +
            "AND retentionState != 'SUPERSEDED' ORDER BY updatedAtEpochMillis DESC, memoryId ASC",
    )
    suspend fun currentLegacyMemories(
        scope: String,
        ownerSessionId: String?,
    ): List<MemoryEntity>

    @Update
    suspend fun updateMemory(entity: MemoryEntity)

    @Query(
        "SELECT * FROM memories WHERE scope = 'USER' AND userVisible = 1 " +
            "ORDER BY updatedAtEpochMillis DESC, memoryId ASC",
    )
    fun observeUserMemories(): Flow<List<MemoryEntity>>

    @Query(
        "SELECT * FROM memories WHERE scope = 'SESSION' AND ownerSessionId = :sessionId " +
            "AND userVisible = 1 " +
            "ORDER BY updatedAtEpochMillis DESC, memoryId ASC",
    )
    fun observeSessionMemories(sessionId: String): Flow<List<MemoryEntity>>

    @Query(
        "SELECT * FROM memories WHERE scope = 'USER' AND retentionState IN ('ACTIVE', 'CONFLICTED') " +
            "AND userVisible = 1 ORDER BY updatedAtEpochMillis DESC, memoryId ASC LIMIT :limit",
    )
    suspend fun userMemoriesForContext(limit: Int): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE scope = 'SESSION' AND ownerSessionId = :sessionId " +
            "AND retentionState IN ('ACTIVE', 'CONFLICTED') AND userVisible = 1 " +
            "ORDER BY updatedAtEpochMillis DESC, memoryId ASC LIMIT :limit",
    )
    suspend fun sessionMemoriesForContext(sessionId: String, limit: Int): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE memoryId = :memoryId")
    suspend fun deleteMemory(memoryId: String): Int

    @Query("DELETE FROM memories WHERE scope = 'SESSION' AND ownerSessionId = :sessionId")
    suspend fun clearSessionMemories(sessionId: String): Int

    @Query("DELETE FROM memories WHERE scope = 'USER'")
    suspend fun clearUserMemories(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionSummary(entity: SessionSummaryEntity)

    @Query("SELECT * FROM session_summaries WHERE summaryId = :summaryId")
    suspend fun sessionSummary(summaryId: String): SessionSummaryEntity?

    @Query(
        "SELECT * FROM session_summaries WHERE sessionId = :sessionId AND status = 'COMPLETED' " +
            "ORDER BY lastCoveredMessageSequence DESC, createdAtEpochMillis DESC, summaryId DESC LIMIT 1",
    )
    suspend fun latestSessionSummary(sessionId: String): SessionSummaryEntity?

    @Query(
        "SELECT * FROM session_summaries WHERE sessionId = :sessionId AND status = 'COMPLETED' " +
            "ORDER BY lastCoveredMessageSequence DESC, createdAtEpochMillis DESC, summaryId DESC LIMIT 1",
    )
    fun observeLatestSessionSummary(sessionId: String): Flow<SessionSummaryEntity?>

    @Query("DELETE FROM session_summaries WHERE summaryId = :summaryId")
    suspend fun deleteSessionSummary(summaryId: String): Int
}
