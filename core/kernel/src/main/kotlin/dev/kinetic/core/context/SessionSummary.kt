package dev.kinetic.core.context

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.memory.SensitiveMemoryFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

const val MAX_SESSION_SUMMARY_LENGTH = 4_000
const val SUMMARY_RECENT_MESSAGE_RESERVE = 4
const val AUTOMATIC_COMPACTION_MIN_UNSUMMARIZED_MESSAGES = 20

enum class SessionSummaryProvenance {
    MODEL_DERIVED,
}

enum class SessionSummaryStatus {
    COMPLETED,
}

data class SessionSummary(
    val summaryId: String,
    val sessionId: String,
    val content: String,
    val firstCoveredMessageSequence: Long,
    val lastCoveredMessageSequence: Long,
    val sourceMessageCount: Int,
    val createdAt: Instant,
    val sourceDigest: String,
    val provenance: SessionSummaryProvenance = SessionSummaryProvenance.MODEL_DERIVED,
    val status: SessionSummaryStatus = SessionSummaryStatus.COMPLETED,
) {
    init {
        require(summaryId.isNotBlank() && summaryId.length <= 200)
        require(sessionId.isNotBlank())
        require(content.isNotBlank() && content.length <= MAX_SESSION_SUMMARY_LENGTH)
        require(firstCoveredMessageSequence > 0)
        require(lastCoveredMessageSequence >= firstCoveredMessageSequence)
        require(sourceMessageCount > 0)
        require(sourceDigest.matches(Regex("[a-f0-9]{64}")))
    }
}

interface SessionSummaryRepository {
    suspend fun latest(sessionId: String): SessionSummary?
    fun observeLatest(sessionId: String): Flow<SessionSummary?>
    suspend fun save(summary: SessionSummary)
    suspend fun delete(summaryId: String): Boolean
}

class InMemorySessionSummaryRepository : SessionSummaryRepository {
    private val mutex = Mutex()
    private val summaries = MutableStateFlow<List<SessionSummary>>(emptyList())

    override suspend fun latest(sessionId: String): SessionSummary? = summaries.value
        .filter { it.sessionId == sessionId && it.status == SessionSummaryStatus.COMPLETED }
        .maxWithOrNull(compareBy<SessionSummary> { it.lastCoveredMessageSequence }.thenBy { it.summaryId })

    override fun observeLatest(sessionId: String): Flow<SessionSummary?> = summaries
        .map { values ->
            values.filter { it.sessionId == sessionId && it.status == SessionSummaryStatus.COMPLETED }
                .maxWithOrNull(
                    compareBy<SessionSummary> { it.lastCoveredMessageSequence }.thenBy { it.summaryId },
                )
        }
        .distinctUntilChanged()

    override suspend fun save(summary: SessionSummary) = mutex.withLock {
        require(summaries.value.none { it.summaryId == summary.summaryId })
        summaries.value = summaries.value + summary
    }

    override suspend fun delete(summaryId: String): Boolean = mutex.withLock {
        if (summaries.value.none { it.summaryId == summaryId }) return@withLock false
        summaries.value = summaries.value.filterNot { it.summaryId == summaryId }
        true
    }
}

data class SummaryGenerationRequest(
    val sessionId: String,
    val previousSummary: SessionSummary?,
    val newMessages: List<AgentMessage>,
) {
    init {
        require(sessionId.isNotBlank())
        require(newMessages.isNotEmpty())
        require(newMessages.all { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT })
        require(newMessages.zipWithNext().all { (left, right) -> left.sequence < right.sequence })
        require(previousSummary == null || previousSummary.sessionId == sessionId)
    }
}

interface ConversationSummaryGenerator {
    suspend fun generateSummary(request: SummaryGenerationRequest): String
}

class DeterministicConversationSummaryGenerator : ConversationSummaryGenerator {
    override suspend fun generateSummary(request: SummaryGenerationRequest): String = buildString {
        request.previousSummary?.let {
            append("Earlier derived context: ")
            append(it.content)
            append('\n')
        }
        request.newMessages.forEach { message ->
            append(if (message.role == MessageRole.USER) "User stated: " else "Assistant stated: ")
            append(message.content.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(500))
            append('\n')
        }
    }.trim().take(MAX_SESSION_SUMMARY_LENGTH)
}

enum class SummaryFailureKind {
    CONFIGURATION,
    CONTEXT_LIMIT,
    AUTHENTICATION,
    NETWORK,
    TIMEOUT,
    RATE_LIMIT,
    SERVER,
    MALFORMED_RESPONSE,
    SENSITIVE_CONTENT,
    UNEXPECTED,
}

class SummaryGenerationException(
    val kind: SummaryFailureKind,
    val safeMessage: String,
    cause: Throwable? = null,
) : RuntimeException(safeMessage, cause)

sealed interface SummaryCompactionResult {
    data class Created(val summary: SessionSummary) : SummaryCompactionResult
    data class NotNeeded(val reason: String) : SummaryCompactionResult
    data class Rejected(val kind: SummaryFailureKind, val userMessage: String) : SummaryCompactionResult
}

class ConversationSummaryService(
    private val repository: SessionSummaryRepository,
    private val generator: ConversationSummaryGenerator,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun compact(
        sessionId: String,
        messages: List<AgentMessage>,
        force: Boolean,
    ): SummaryCompactionResult {
        val previous = repository.latest(sessionId)
        val eligible = messages.asSequence()
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filter { it.sequence > (previous?.lastCoveredMessageSequence ?: 0L) }
            .sortedBy(AgentMessage::sequence)
            .toList()
        val threshold = if (force) SUMMARY_RECENT_MESSAGE_RESERVE + 1 else {
            AUTOMATIC_COMPACTION_MIN_UNSUMMARIZED_MESSAGES + SUMMARY_RECENT_MESSAGE_RESERVE
        }
        if (eligible.size < threshold) {
            return SummaryCompactionResult.NotNeeded(
                if (force) "Add more conversation messages before compacting." else "Threshold not reached.",
            )
        }
        val newSource = eligible.dropLast(SUMMARY_RECENT_MESSAGE_RESERVE)
        if (newSource.any { SensitiveMemoryFilter.isSensitive(it.content) }) {
            return SummaryCompactionResult.Rejected(
                SummaryFailureKind.SENSITIVE_CONTENT,
                "Kinetic did not compact this range because it contains credential-like content.",
            )
        }
        val generated = try {
            generator.generateSummary(SummaryGenerationRequest(sessionId, previous, newSource)).trim()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SummaryGenerationException) {
            return SummaryCompactionResult.Rejected(failure.kind, failure.safeMessage)
        } catch (_: Throwable) {
            return SummaryCompactionResult.Rejected(
                SummaryFailureKind.UNEXPECTED,
                "Kinetic could not compact this conversation. Existing context remains available.",
            )
        }
        if (generated.isBlank() || generated.length > MAX_SESSION_SUMMARY_LENGTH) {
            return SummaryCompactionResult.Rejected(
                SummaryFailureKind.MALFORMED_RESPONSE,
                "The summary response was empty or exceeded Kinetic's local limit.",
            )
        }
        if (SensitiveMemoryFilter.isSensitive(generated)) {
            return SummaryCompactionResult.Rejected(
                SummaryFailureKind.SENSITIVE_CONTENT,
                "Kinetic rejected a summary containing credential-like content.",
            )
        }
        val summary = SessionSummary(
            summaryId = ids.nextId("summary"),
            sessionId = sessionId,
            content = generated,
            firstCoveredMessageSequence = previous?.firstCoveredMessageSequence
                ?: newSource.first().sequence,
            lastCoveredMessageSequence = newSource.last().sequence,
            sourceMessageCount = (previous?.sourceMessageCount ?: 0) + newSource.size,
            createdAt = clock.instant(),
            sourceDigest = summarySourceDigest(sessionId, previous, newSource),
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        repository.save(summary)
        return SummaryCompactionResult.Created(summary)
    }
}

fun summarySourceDigest(
    sessionId: String,
    previous: SessionSummary?,
    messages: List<AgentMessage>,
): String {
    val canonical = buildString {
        append(sessionId)
        append('\u0000')
        append(previous?.sourceDigest.orEmpty())
        messages.forEach {
            append('\u0000')
            append(it.sequence)
            append('\u0000')
            append(it.role.name)
            append('\u0000')
            append(it.content)
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
