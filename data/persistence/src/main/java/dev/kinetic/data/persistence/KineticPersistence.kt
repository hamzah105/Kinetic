package dev.kinetic.data.persistence

import android.content.Context
import dev.kinetic.core.session.RunLedger
import dev.kinetic.core.session.SessionStore
import dev.kinetic.core.memory.MemoryRepository
import dev.kinetic.core.context.SessionSummaryRepository

/** Android-facing composition root; Room types do not leak into the app or kernel APIs. */
class KineticPersistence private constructor(
    private val database: KineticDatabase,
) {
    val sessionStore: SessionStore = RoomSessionStore(database)
    val runLedger: RunLedger = RoomRunLedger(database)
    val memoryRepository: MemoryRepository = RoomMemoryRepository(database)
    val sessionSummaryRepository: SessionSummaryRepository = RoomSessionSummaryRepository(database)

    fun close() = database.close()

    companion object {
        fun create(context: Context): KineticPersistence =
            KineticPersistence(KineticDatabase.create(context))
    }
}
