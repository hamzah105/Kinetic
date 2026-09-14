package dev.kinetic.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        JournalEntryEntity::class,
        TurnEntity::class,
        ApprovalEntity::class,
        EffectEntity::class,
        MemoryEntity::class,
        SessionSummaryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
internal abstract class KineticDatabase : RoomDatabase() {
    abstract fun kineticDao(): KineticDao

    companion object {
        const val DATABASE_NAME = "kinetic-phase1.db"

        fun create(context: Context): KineticDatabase = open(context, DATABASE_NAME)

        internal fun open(context: Context, databaseName: String): KineticDatabase = Room.databaseBuilder(
            context.applicationContext,
            KineticDatabase::class.java,
            databaseName,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        ).build()

        fun inMemory(context: Context): KineticDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            KineticDatabase::class.java,
        ).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0",
                )
                db.query(
                    "SELECT messageId, sessionId FROM messages " +
                        "ORDER BY sessionId, createdAtEpochMillis, messageId",
                ).use { cursor ->
                    var sessionId: String? = null
                    var sequence = 0L
                    while (cursor.moveToNext()) {
                        val rowSessionId = cursor.getString(1)
                        if (rowSessionId != sessionId) {
                            sessionId = rowSessionId
                            sequence = 1L
                        } else {
                            sequence += 1L
                        }
                        db.execSQL(
                            "UPDATE messages SET sequence = ? WHERE messageId = ?",
                            arrayOf(sequence, cursor.getString(0)),
                        )
                    }
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_messages_sessionId_sequence ON messages (sessionId, sequence)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE approvals ADD COLUMN capabilityCategory TEXT NOT NULL " +
                        "DEFAULT 'DEMONSTRATION'",
                )
                db.execSQL(
                    "ALTER TABLE approvals ADD COLUMN crossesApplicationBoundary INTEGER NOT NULL " +
                        "DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE effects ADD COLUMN inputBinding TEXT NOT NULL " +
                        "DEFAULT 'legacy-unbound-input'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (" +
                        "`memoryId` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                        "`provenance` TEXT NOT NULL, `ownerSessionId` TEXT, " +
                        "`sourceSessionId` TEXT NOT NULL, `sourceTurnId` TEXT NOT NULL, " +
                        "`sourceMessageId` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                        "`updatedAtEpochMillis` INTEGER NOT NULL, `userVisible` INTEGER NOT NULL, " +
                        "`retentionState` TEXT NOT NULL, `deduplicationKey` TEXT NOT NULL, " +
                        "PRIMARY KEY(`memoryId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_scope` ON `memories` (`scope`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memories_ownerSessionId` " +
                        "ON `memories` (`ownerSessionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memories_scope_updatedAtEpochMillis` " +
                        "ON `memories` (`scope`, `updatedAtEpochMillis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memories_ownerSessionId_updatedAtEpochMillis` " +
                        "ON `memories` (`ownerSessionId`, `updatedAtEpochMillis`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_memories_deduplicationKey` " +
                        "ON `memories` (`deduplicationKey`)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_summaries` (" +
                        "`summaryId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `firstCoveredMessageSequence` INTEGER NOT NULL, " +
                        "`lastCoveredMessageSequence` INTEGER NOT NULL, " +
                        "`sourceMessageCount` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                        "`sourceDigest` TEXT NOT NULL, `provenance` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, PRIMARY KEY(`summaryId`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_summaries_sessionId` " +
                        "ON `session_summaries` (`sessionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_summaries_sessionId_lastCoveredMessageSequence` " +
                        "ON `session_summaries` (`sessionId`, `lastCoveredMessageSequence`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_session_summaries_sourceDigest` " +
                        "ON `session_summaries` (`sourceDigest`)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN governanceKey TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN governanceSubject TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN governanceValue TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN supersedesMemoryId TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN supersededByMemoryId TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_memories_deduplicationKey")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memories_deduplicationKey " +
                        "ON memories (deduplicationKey)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_memories_scope_ownerSessionId_governanceKey_retentionState " +
                        "ON memories (scope, ownerSessionId, governanceKey, retentionState)",
                )
            }
        }
    }
}
