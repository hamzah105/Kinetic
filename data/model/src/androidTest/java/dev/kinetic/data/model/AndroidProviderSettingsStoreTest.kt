package dev.kinetic.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.IdGenerator
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.context.ConversationSummaryService
import dev.kinetic.core.context.DeterministicConversationSummaryGenerator
import dev.kinetic.core.context.InMemorySessionSummaryRepository
import dev.kinetic.core.context.SummaryCompactionResult
import dev.kinetic.data.persistence.KineticPersistence
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidProviderSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun separate_provider_keys_survive_blank_saves_switches_profiles_and_reopen() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val compatiblePair = encryptedPair()
        store.saveOpenAi(OpenAiConfiguration(), REPLACEMENT_SYNTHETIC_KEY)
        val openAiPair = providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_CIPHERTEXT, null) to
            providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_IV, null)
        ProviderMode.entries.forEach { store.setMode(it) }
        store.saveConfiguration(configuration().copy(modelId = "other-model"), " ")
        store.saveOpenAi(OpenAiConfiguration(profile = dev.kinetic.core.model.ModelProfile.DEEP, structuredTools = true), " ")
        store.setAutomaticContextCompactionEnabled(true)
        val reopened = AndroidProviderSettingsStore(context)
        assertEquals(SYNTHETIC_KEY, reopened.apiKey())
        assertEquals(REPLACEMENT_SYNTHETIC_KEY, reopened.openAiApiKey())
        assertEquals(compatiblePair, encryptedPair())
        assertEquals(openAiPair.first, providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_CIPHERTEXT, null))
        assertEquals(openAiPair.second, providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_IV, null))
        assertEquals(dev.kinetic.core.model.ModelProfile.DEEP, reopened.current().openAi.profile)
        assertPreferencesDoNotContain(SYNTHETIC_KEY)
        assertPreferencesDoNotContain(REPLACEMENT_SYNTHETIC_KEY)
        clearPreferences()
    }

    @Test
    fun clear_removes_only_the_explicitly_selected_credential() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        store.saveOpenAi(OpenAiConfiguration(), REPLACEMENT_SYNTHETIC_KEY)
        store.clearOpenAiKey()
        assertNull(store.openAiApiKey()); assertEquals(SYNTHETIC_KEY, store.apiKey())
        store.saveOpenAi(OpenAiConfiguration(), REPLACEMENT_SYNTHETIC_KEY)
        store.clearApiKey()
        assertNull(store.apiKey()); assertEquals(REPLACEMENT_SYNTHETIC_KEY, store.openAiApiKey())
        clearPreferences()
    }

    @Test
    fun openai_decrypt_failure_retains_both_providers_and_does_not_generate_a_key() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        store.saveOpenAi(OpenAiConfiguration(), REPLACEMENT_SYNTHETIC_KEY)
        val iv = providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_IV, null)
        providerPreferences().edit().putString(AndroidProviderSettingsStore.KEY_OPENAI_CIPHERTEXT, "invalid-encrypted-value").commit()
        assertFailsWith<ProviderCredentialUnavailableException> { store.openAiApiKey() }
        store.setMode(ProviderMode.FAKE)
        store.saveOpenAi(OpenAiConfiguration(), "")
        assertEquals("invalid-encrypted-value", providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_CIPHERTEXT, null))
        assertEquals(iv, providerPreferences().getString(AndroidProviderSettingsStore.KEY_OPENAI_IV, null))
        assertEquals(SYNTHETIC_KEY, store.apiKey())
        clearPreferences()
    }

    @Test
    fun openai_configuration_does_not_copy_compatible_credential_or_touch_room() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        store.saveOpenAi(OpenAiConfiguration(), "")
        assertFalse(store.current().hasOpenAiKey)
        assertNull(store.openAiApiKey())
        context.deleteDatabase(DATABASE_NAME)
        val persistence = KineticPersistence.create(context)
        try {
            persistence.sessionStore.getOrCreate("openai-security", Instant.EPOCH)
            store.saveOpenAi(OpenAiConfiguration(), REPLACEMENT_SYNTHETIC_KEY)
            val session = persistence.sessionStore.get("openai-security")!!
            assertTrue(session.messages.isEmpty()); assertTrue(session.journal.isEmpty())
        } finally { persistence.close() }
        val bytes = context.getDatabasePath(DATABASE_NAME).readBytes()
        assertFalse(bytes.containsSubsequence(SYNTHETIC_KEY.toByteArray()))
        assertFalse(bytes.containsSubsequence(REPLACEMENT_SYNTHETIC_KEY.toByteArray()))
        context.deleteDatabase(DATABASE_NAME)
        clearPreferences()
    }

    @Test
    fun decrypt_failure_retains_ciphertext_and_requires_explicit_replacement() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val preferences = providerPreferences()
        val originalIv = preferences.getString(AndroidProviderSettingsStore.KEY_IV, null)

        preferences.edit()
            .putString(AndroidProviderSettingsStore.KEY_CIPHERTEXT, "not-valid-base64")
            .commit()

        assertFailsWith<ProviderCredentialUnavailableException> { store.apiKey() }
        assertEquals(
            "not-valid-base64",
            preferences.getString(AndroidProviderSettingsStore.KEY_CIPHERTEXT, null),
        )
        assertEquals(originalIv, preferences.getString(AndroidProviderSettingsStore.KEY_IV, null))
        assertTrue(store.settings.value.hasApiKey)
        clearPreferences()
    }

    @Test
    fun api_key_can_be_saved_replaced_and_cleared_without_plaintext_preferences() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val firstEncrypted = encryptedPair()

        assertEquals(SYNTHETIC_KEY, store.apiKey())
        assertTrue(store.settings.value.hasApiKey)
        assertEquals("secure-model", store.settings.value.cloud.modelId)
        assertTrue(store.settings.value.cloud.structuredToolCallingEnabled)
        assertPreferencesDoNotContain(SYNTHETIC_KEY)

        store.replaceApiKey(REPLACEMENT_SYNTHETIC_KEY)
        assertEquals(REPLACEMENT_SYNTHETIC_KEY, store.apiKey())
        assertNotEquals(firstEncrypted, encryptedPair())
        assertPreferencesDoNotContain(SYNTHETIC_KEY)
        assertPreferencesDoNotContain(REPLACEMENT_SYNTHETIC_KEY)

        store.clearApiKey()
        assertNull(store.apiKey())
        assertFalse(store.settings.value.hasApiKey)
        assertFalse(providerPreferences().contains(AndroidProviderSettingsStore.KEY_CIPHERTEXT))
        assertFalse(providerPreferences().contains(AndroidProviderSettingsStore.KEY_IV))
        clearPreferences()
    }

    @Test
    fun synthetic_key_survives_reopen_and_fake_cloud_round_trips() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val encrypted = encryptedPair()

        assertEquals(SYNTHETIC_KEY, AndroidProviderSettingsStore(context).apiKey())
        store.setMode(ProviderMode.CLOUD)
        assertEquals(SYNTHETIC_KEY, store.apiKey())
        store.setMode(ProviderMode.FAKE)
        store.setMode(ProviderMode.CLOUD)
        store.setAutomaticContextCompactionEnabled(true)
        assertEquals(SYNTHETIC_KEY, store.apiKey())
        assertEquals(encrypted, encryptedPair())
        clearPreferences()
    }

    @Test
    fun blank_key_save_retains_key_while_base_url_and_model_change() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val encrypted = encryptedPair()

        store.saveConfiguration(
            configuration().copy(baseUrl = "https://one.example/v1"),
            newApiKey = "   ",
        )
        store.saveConfiguration(
            configuration().copy(baseUrl = "https://one.example/v1", modelId = "retained-model"),
            newApiKey = null,
        )

        assertEquals(SYNTHETIC_KEY, store.apiKey())
        assertEquals("https://one.example/v1", store.settings.value.cloud.baseUrl)
        assertEquals("retained-model", store.settings.value.cloud.modelId)
        assertEquals(encrypted, encryptedPair())
        clearPreferences()
    }

    @Test
    fun repeated_store_reconstruction_retains_synthetic_key() = runBlocking {
        clearPreferences()
        AndroidProviderSettingsStore(context).saveConfiguration(configuration(), SYNTHETIC_KEY)

        repeat(3) {
            val reconstructed = AndroidProviderSettingsStore(context)
            assertTrue(reconstructed.settings.value.hasApiKey)
            assertEquals(SYNTHETIC_KEY, reconstructed.apiKey())
        }
        clearPreferences()
    }

    @Test
    fun failed_settings_validation_does_not_delete_existing_key() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val encrypted = encryptedPair()

        assertFailsWith<IllegalArgumentException> {
            store.saveConfiguration(configuration().copy(baseUrl = "http://insecure.example/v1"), null)
        }

        assertEquals(SYNTHETIC_KEY, store.apiKey())
        assertEquals(encrypted, encryptedPair())
        clearPreferences()
    }

    @Test
    fun context_compaction_does_not_touch_secure_preferences() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)
        store.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val encrypted = encryptedPair()
        val service = ConversationSummaryService(
            repository = InMemorySessionSummaryRepository(),
            generator = DeterministicConversationSummaryGenerator(),
            ids = object : IdGenerator {
                override fun nextId(prefix: String) = "$prefix-retention-test"
            },
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val messages = (1..6).map { sequence ->
            AgentMessage(
                messageId = "message-$sequence",
                turnId = "turn-$sequence",
                role = if (sequence % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "ordinary safe context $sequence",
                createdAt = Instant.EPOCH.plusSeconds(sequence.toLong()),
                sequence = sequence.toLong(),
            )
        }

        assertIs<SummaryCompactionResult.Created>(service.compact("retention-session", messages, true))
        assertEquals(SYNTHETIC_KEY, store.apiKey())
        assertEquals(encrypted, encryptedPair())
        clearPreferences()
    }

    @Test
    fun api_key_is_not_written_to_room_conversation_storage() = runBlocking {
        clearPreferences()
        context.deleteDatabase(DATABASE_NAME)
        val settings = AndroidProviderSettingsStore(context)
        settings.saveConfiguration(configuration(), SYNTHETIC_KEY)
        val persistence = KineticPersistence.create(context)
        try {
            val now = Instant.parse("2026-08-28T14:00:00Z")
            persistence.sessionStore.getOrCreate("secret-boundary", now)
            persistence.sessionStore.appendMessage(
                "secret-boundary",
                AgentMessage(
                    messageId = "safe-message",
                    turnId = "safe-turn",
                    role = MessageRole.USER,
                    content = "normal conversation text",
                    createdAt = now,
                ),
            )
            val restored = persistence.sessionStore.get("secret-boundary")!!
            assertTrue(restored.messages.none { it.content.contains(SYNTHETIC_KEY) })
            assertTrue(restored.journal.none { it.toString().contains(SYNTHETIC_KEY) })
        } finally {
            persistence.close()
        }

        val databaseBytes = context.getDatabasePath(DATABASE_NAME).readBytes()
        assertFalse(databaseBytes.containsSubsequence(SYNTHETIC_KEY.toByteArray()))
        context.deleteDatabase(DATABASE_NAME)
        clearPreferences()
    }

    @Test
    fun automatic_context_compaction_defaults_off_and_persists_explicit_opt_in() = runBlocking {
        clearPreferences()
        val store = AndroidProviderSettingsStore(context)

        assertFalse(store.settings.value.automaticContextCompactionEnabled)
        store.setAutomaticContextCompactionEnabled(true)

        val reopened = AndroidProviderSettingsStore(context)
        assertTrue(reopened.settings.value.automaticContextCompactionEnabled)
        reopened.setAutomaticContextCompactionEnabled(false)
        assertFalse(reopened.settings.value.automaticContextCompactionEnabled)
        clearPreferences()
    }

    private fun configuration() = CloudProviderConfiguration(
        displayName = "Secure Test",
        baseUrl = "https://example.com/v1",
        modelId = "secure-model",
        structuredToolCallingEnabled = true,
    )

    private fun clearPreferences() {
        providerPreferences().edit().clear().commit()
    }

    private fun assertPreferencesDoNotContain(secret: String) {
        val values = providerPreferences().all.values.joinToString("|")
        assertFalse(values.contains(secret))
    }

    private fun encryptedPair(): Pair<String?, String?> =
        providerPreferences().getString(AndroidProviderSettingsStore.KEY_CIPHERTEXT, null) to
            providerPreferences().getString(AndroidProviderSettingsStore.KEY_IV, null)

    private fun providerPreferences() = context.getSharedPreferences(
            AndroidProviderSettingsStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    private fun ByteArray.containsSubsequence(target: ByteArray): Boolean {
        if (target.isEmpty() || target.size > size) return false
        return indices.take(size - target.size + 1).any { offset ->
            target.indices.all { index -> this[offset + index] == target[index] }
        }
    }

    private companion object {
        const val DATABASE_NAME = "kinetic-phase1.db"
        const val SYNTHETIC_KEY = "KINETIC_TEST_SECRET_DO_NOT_USE_REAL_KEY"
        const val REPLACEMENT_SYNTHETIC_KEY = "KINETIC_TEST_SECRET_REPLACEMENT_DO_NOT_USE_REAL_KEY"
    }
}
