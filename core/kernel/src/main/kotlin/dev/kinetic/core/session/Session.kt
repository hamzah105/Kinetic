package dev.kinetic.core.session

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.logging.JournalEntry
import dev.kinetic.core.logging.JournalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class AgentSession(
    val sessionId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<AgentMessage> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
)

/** Persistence seam. A Room implementation can replace this store without changing the kernel. */
interface SessionStore {
    suspend fun getOrCreate(sessionId: String, now: Instant): AgentSession
    suspend fun get(sessionId: String): AgentSession?
    fun observe(sessionId: String): Flow<AgentSession?>
    suspend fun appendMessage(sessionId: String, message: AgentMessage): AgentSession
    suspend fun appendJournalEvent(
        sessionId: String,
        turnId: String,
        entryId: String,
        occurredAt: Instant,
        event: JournalEvent,
    ): JournalEntry
}

class InMemorySessionStore : SessionStore {
    private val mutex = Mutex()
    private val sessions = MutableStateFlow<Map<String, AgentSession>>(emptyMap())

    override suspend fun getOrCreate(sessionId: String, now: Instant): AgentSession =
        mutex.withLock {
            sessions.value[sessionId] ?: AgentSession(sessionId, now, now).also { created ->
                sessions.value = sessions.value + (sessionId to created)
            }
        }

    override suspend fun get(sessionId: String): AgentSession? = sessions.value[sessionId]

    override fun observe(sessionId: String): Flow<AgentSession?> = sessions
        .map { it[sessionId] }
        .distinctUntilChanged()

    override suspend fun appendMessage(sessionId: String, message: AgentMessage): AgentSession =
        mutex.withLock {
            val current = requireNotNull(sessions.value[sessionId]) {
                "Session $sessionId must be created before appending a message"
            }
            val updated = current.copy(
                updatedAt = maxOf(current.updatedAt, message.createdAt),
                messages = current.messages + message.copy(
                    sequence = (current.messages.lastOrNull()?.sequence ?: 0L) + 1L,
                ),
            )
            sessions.value = sessions.value + (sessionId to updated)
            updated
        }

    override suspend fun appendJournalEvent(
        sessionId: String,
        turnId: String,
        entryId: String,
        occurredAt: Instant,
        event: JournalEvent,
    ): JournalEntry = mutex.withLock {
        val current = requireNotNull(sessions.value[sessionId]) {
            "Session $sessionId must be created before appending a journal event"
        }
        val entry = JournalEntry(
            entryId = entryId,
            sessionId = sessionId,
            turnId = turnId,
            sequence = (current.journal.lastOrNull()?.sequence ?: 0L) + 1L,
            occurredAt = occurredAt,
            event = event,
        )
        val updated = current.copy(
            updatedAt = maxOf(current.updatedAt, occurredAt),
            journal = current.journal + entry,
        )
        sessions.value = sessions.value + (sessionId to updated)
        entry
    }
}
