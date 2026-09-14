package dev.kinetic.data.persistence

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KineticDatabase::class.java,
    )

    @Test
    fun migration_2_3_adds_capability_metadata_and_argument_binding_without_data_loss() {
        val name = "migration-2-3"
        helper.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO sessions(sessionId, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('session', 0, 0)",
            )
            execSQL(
                "INSERT INTO turns(turnId, sessionId, requestId, requestSummary, status, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('turn', 'session', 'request', 'summary', 'THINKING', 0, 0)",
            )
            execSQL(
                "INSERT INTO effects(callId, sessionId, turnId, toolId, status, " +
                    "createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('call', 'session', 'turn', 'echo', 'PENDING', 0, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 3, true, KineticDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT toolId, inputBinding FROM effects WHERE callId = 'call'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("echo", cursor.getString(0))
                assertEquals("legacy-unbound-input", cursor.getString(1))
            }
        }
    }

    @Test
    fun migration_3_4_adds_controlled_memory_table_without_changing_existing_data() {
        val name = "migration-3-4"
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO sessions(sessionId, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('preserved-session', 1, 2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 4, true, KineticDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT updatedAtEpochMillis FROM sessions WHERE sessionId = 'preserved-session'")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(2L, cursor.getLong(0))
                }
            db.query("SELECT COUNT(*) FROM memories").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration_4_5_adds_session_summaries_without_changing_history_or_memory() {
        val name = "migration-4-5"
        helper.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO sessions(sessionId, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('preserved-session', 1, 2)",
            )
            execSQL(
                "INSERT INTO messages(messageId, sessionId, turnId, role, content, " +
                    "createdAtEpochMillis, sequence) VALUES('preserved-message', " +
                    "'preserved-session', 'turn', 'USER', 'original history', 3, 1)",
            )
            execSQL(
                "INSERT INTO memories(memoryId, scope, category, content, provenance, " +
                    "ownerSessionId, sourceSessionId, sourceTurnId, sourceMessageId, " +
                    "createdAtEpochMillis, updatedAtEpochMillis, userVisible, retentionState, " +
                    "deduplicationKey) VALUES('preserved-memory', 'USER', 'PREFERENCE', " +
                    "'preferred database PostgreSQL', 'USER_EXPLICIT', NULL, " +
                    "'preserved-session', 'turn', 'preserved-message', 3, 3, 1, " +
                    "'ACTIVE', 'USER|preferred database postgresql')",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 5, true, KineticDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT content FROM messages WHERE messageId = 'preserved-message'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("original history", cursor.getString(0))
            }
            db.query("SELECT content FROM memories WHERE memoryId = 'preserved-memory'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("preferred database PostgreSQL", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM session_summaries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration_4_5_does_not_touch_provider_secure_preferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(PROVIDER_PREFERENCES, Context.MODE_PRIVATE)
        val expected = mapOf(
            PROVIDER_CIPHERTEXT to "synthetic-encrypted-bytes",
            PROVIDER_IV to "synthetic-gcm-iv",
            PROVIDER_MODE to "CLOUD",
        )
        preferences.edit()
            .putString(PROVIDER_CIPHERTEXT, expected.getValue(PROVIDER_CIPHERTEXT))
            .putString(PROVIDER_IV, expected.getValue(PROVIDER_IV))
            .putString(PROVIDER_MODE, expected.getValue(PROVIDER_MODE))
            .commit()
        val name = "migration-4-5-provider-preferences"
        try {
            helper.createDatabase(name, 4).close()
            helper.runMigrationsAndValidate(name, 5, true, KineticDatabase.MIGRATION_4_5).close()

            assertEquals(expected, preferences.all)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun migration_5_6_adds_governance_metadata_without_changing_memory_or_history() {
        val name = "migration-5-6"
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO sessions(sessionId, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES('preserved-session', 1, 2)",
            )
            execSQL(
                "INSERT INTO messages(messageId, sessionId, turnId, role, content, " +
                    "createdAtEpochMillis, sequence) VALUES('preserved-message', " +
                    "'preserved-session', 'turn', 'USER', 'original history', 3, 1)",
            )
            execSQL(
                "INSERT INTO memories(memoryId, scope, category, content, provenance, " +
                    "ownerSessionId, sourceSessionId, sourceTurnId, sourceMessageId, " +
                    "createdAtEpochMillis, updatedAtEpochMillis, userVisible, retentionState, " +
                    "deduplicationKey) VALUES('preserved-memory', 'USER', 'PREFERENCE', " +
                    "'my preferred database is PostgreSQL', 'USER_EXPLICIT', NULL, " +
                    "'preserved-session', 'turn', 'preserved-message', 3, 3, 1, " +
                    "'ACTIVE', 'preserved-deduplication-key')",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 6, true, KineticDatabase.MIGRATION_5_6).use { db ->
            db.query(
                "SELECT content, retentionState, governanceKey, supersededByMemoryId " +
                    "FROM memories WHERE memoryId = 'preserved-memory'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("my preferred database is PostgreSQL", cursor.getString(0))
                assertEquals("ACTIVE", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
            }
            db.query("SELECT content FROM messages WHERE messageId = 'preserved-message'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("original history", cursor.getString(0))
            }
        }
    }

    @Test
    fun migration_5_6_does_not_touch_provider_secure_preferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(PROVIDER_PREFERENCES, Context.MODE_PRIVATE)
        val expected = mapOf(
            PROVIDER_CIPHERTEXT to "synthetic-encrypted-bytes",
            PROVIDER_IV to "synthetic-gcm-iv",
            PROVIDER_MODE to "CLOUD",
        )
        preferences.edit()
            .putString(PROVIDER_CIPHERTEXT, expected.getValue(PROVIDER_CIPHERTEXT))
            .putString(PROVIDER_IV, expected.getValue(PROVIDER_IV))
            .putString(PROVIDER_MODE, expected.getValue(PROVIDER_MODE))
            .commit()
        val name = "migration-5-6-provider-preferences"
        try {
            helper.createDatabase(name, 5).close()
            helper.runMigrationsAndValidate(name, 6, true, KineticDatabase.MIGRATION_5_6).close()
            assertEquals(expected, preferences.all)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private companion object {
        const val PROVIDER_PREFERENCES = "kinetic_provider_settings"
        const val PROVIDER_CIPHERTEXT = "provider_api_key_ciphertext"
        const val PROVIDER_IV = "provider_api_key_iv"
        const val PROVIDER_MODE = "provider_mode"
    }
}
