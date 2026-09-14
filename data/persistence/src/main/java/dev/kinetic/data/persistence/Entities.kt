package dev.kinetic.data.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "turnId"]),
        Index(value = ["sessionId", "sequence"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val turnId: String,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val sequence: Long,
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("turnId"),
        Index(value = ["sessionId", "sequence"], unique = true),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey val entryId: String,
    val sessionId: String,
    val turnId: String,
    val sequence: Long,
    val occurredAtEpochMillis: Long,
    val eventType: String,
    val primaryId: String? = null,
    val secondaryId: String? = null,
    val toolId: String? = null,
    val summary: String? = null,
    val listValue: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val fromPhase: String? = null,
    val toPhase: String? = null,
)

@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "createdAtEpochMillis"])],
)
data class TurnEntity(
    @PrimaryKey val turnId: String,
    val sessionId: String,
    val requestId: String,
    val requestSummary: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completionSummary: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

@Entity(
    tableName = "approvals",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["turnId"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["turnId"], unique = true)],
)
data class ApprovalEntity(
    @PrimaryKey val approvalId: String,
    val sessionId: String,
    val turnId: String,
    val callId: String,
    val toolId: String,
    val inputKind: String,
    val inputPayload: String? = null,
    val riskLevel: String,
    val requiresConfirmation: Boolean,
    val requiredPermissions: String,
    val distributionAvailability: String,
    val capabilityCategory: String,
    val crossesApplicationBoundary: Boolean,
    val status: String,
    val requestedAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "effects",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["turnId"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["turnId"], unique = true)],
)
data class EffectEntity(
    @PrimaryKey val callId: String,
    val sessionId: String,
    val turnId: String,
    val toolId: String,
    val inputBinding: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val outputSummary: String? = null,
    val errorCode: String? = null,
)

@Entity(
    tableName = "memories",
    indices = [
        Index("scope"),
        Index("ownerSessionId"),
        Index(value = ["scope", "updatedAtEpochMillis"]),
        Index(value = ["ownerSessionId", "updatedAtEpochMillis"]),
        Index("deduplicationKey"),
        Index(value = ["scope", "ownerSessionId", "governanceKey", "retentionState"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey val memoryId: String,
    val scope: String,
    val category: String,
    val content: String,
    val provenance: String,
    val ownerSessionId: String?,
    val sourceSessionId: String,
    val sourceTurnId: String,
    val sourceMessageId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val userVisible: Boolean,
    val retentionState: String,
    val deduplicationKey: String,
    val governanceKey: String?,
    val governanceSubject: String?,
    val governanceValue: String?,
    val supersedesMemoryId: String?,
    val supersededByMemoryId: String?,
)

@Entity(
    tableName = "session_summaries",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "lastCoveredMessageSequence"]),
        Index(value = ["sourceDigest"], unique = true),
    ],
)
data class SessionSummaryEntity(
    @PrimaryKey val summaryId: String,
    val sessionId: String,
    val content: String,
    val firstCoveredMessageSequence: Long,
    val lastCoveredMessageSequence: Long,
    val sourceMessageCount: Int,
    val createdAtEpochMillis: Long,
    val sourceDigest: String,
    val provenance: String,
    val status: String,
)
