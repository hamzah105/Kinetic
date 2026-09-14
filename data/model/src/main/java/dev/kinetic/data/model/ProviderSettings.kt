package dev.kinetic.data.model

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.kinetic.core.model.ModelProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ProviderMode {
    FAKE,
    CLOUD,
    OPENAI,
    LOCAL_SIMULATED,
    LOCAL_LLAMA,
}

data class CloudProviderConfiguration(
    val displayName: String = "OpenAI-compatible cloud",
    val baseUrl: String = "https://api.openai.com/v1",
    val modelId: String = "",
    val requestTimeoutSeconds: Long = 45,
    val structuredToolCallingEnabled: Boolean = false,
) {
    fun validated(): CloudProviderConfiguration {
        val name = displayName.trim().ifEmpty { "OpenAI-compatible cloud" }
        val model = modelId.trim()
        require(model.isNotEmpty()) { "Model ID is required." }
        require(model.length <= 200) { "Model ID is too long." }
        require(requestTimeoutSeconds in 5..120) { "Timeout must be between 5 and 120 seconds." }

        val uri = try {
            URI(baseUrl.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("Base URL is not a valid URL.")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Base URL must use HTTPS." }
        require(!uri.host.isNullOrBlank()) { "Base URL must include a host." }
        require(uri.userInfo == null) { "Base URL must not contain credentials." }
        require(uri.query == null && uri.fragment == null) {
            "Base URL must not contain a query or fragment."
        }
        val normalizedUrl = uri.toString().trimEnd('/')
        return copy(
            displayName = name.take(80),
            baseUrl = normalizedUrl,
            modelId = model,
        )
    }
}

data class ProviderSettingsSnapshot(
    val routingMode: dev.kinetic.core.model.RoutingMode = dev.kinetic.core.model.RoutingMode.MANUAL,
    val routingLocalOnly: Boolean = false,
    val routingToolsRequired: Boolean = false,
    val mode: ProviderMode = ProviderMode.FAKE,
    val cloud: CloudProviderConfiguration = CloudProviderConfiguration(),
    val hasApiKey: Boolean = false,
    val automaticContextCompactionEnabled: Boolean = false,
    val openAi: OpenAiConfiguration = OpenAiConfiguration(),
    val hasOpenAiKey: Boolean = false,
)

interface ProviderSettingsStore {
    val settings: StateFlow<ProviderSettingsSnapshot>

    fun current(): ProviderSettingsSnapshot = settings.value
    suspend fun saveConfiguration(configuration: CloudProviderConfiguration, newApiKey: String? = null)
    suspend fun setMode(mode: ProviderMode)
    suspend fun setRouting(mode: dev.kinetic.core.model.RoutingMode, localOnly: Boolean, toolsRequired: Boolean) {
        throw UnsupportedOperationException("Routing settings unavailable")
    }
    suspend fun replaceApiKey(apiKey: String)
    suspend fun clearApiKey()
    suspend fun setAutomaticContextCompactionEnabled(enabled: Boolean)
    suspend fun apiKey(): String?
    suspend fun saveOpenAi(configuration: OpenAiConfiguration, newApiKey: String? = null) {
        throw UnsupportedOperationException("Direct OpenAI settings unavailable")
    }
    suspend fun openAiApiKey(): String? = null
    suspend fun clearOpenAiKey() { throw UnsupportedOperationException("Direct OpenAI settings unavailable") }
}

class ProviderCredentialUnavailableException : IllegalStateException(
    "The stored API key could not be decrypted. Clear and replace it explicitly.",
)

/**
 * Stores only ciphertext in private preferences. The AES key is non-exportable and lives in the
 * Android Keystore; provider metadata remains ordinary non-secret app configuration.
 */
class AndroidProviderSettingsStore(context: Context) : ProviderSettingsStore {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(loadSnapshot())
    override val settings: StateFlow<ProviderSettingsSnapshot> = mutableSettings.asStateFlow()

    override suspend fun saveConfiguration(
        configuration: CloudProviderConfiguration,
        newApiKey: String?,
    ) = mutex.withLock {
        val validated = configuration.validated()
        val editor = preferences.edit()
            .putString(KEY_DISPLAY_NAME, validated.displayName)
            .putString(KEY_BASE_URL, validated.baseUrl)
            .putString(KEY_MODEL_ID, validated.modelId)
            .putLong(KEY_TIMEOUT_SECONDS, validated.requestTimeoutSeconds)
            .putBoolean(KEY_STRUCTURED_TOOLS, validated.structuredToolCallingEnabled)
        if (!newApiKey.isNullOrBlank()) {
            val encrypted = encrypt(newApiKey.trim())
            editor.putString(KEY_CIPHERTEXT, encrypted.ciphertext)
            editor.putString(KEY_IV, encrypted.iv)
        }
        val committed = if (newApiKey.isNullOrBlank()) {
            commitRetainingStoredSecret(editor)
        } else {
            editor.commit()
        }
        check(committed) { "Provider configuration could not be saved." }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun setMode(mode: ProviderMode) = mutex.withLock {
        check(commitRetainingStoredSecret(preferences.edit().putString(KEY_MODE, mode.name).putString("routing_mode", "MANUAL"))) {
            "Provider mode could not be saved."
        }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun setRouting(mode: dev.kinetic.core.model.RoutingMode, localOnly: Boolean, toolsRequired: Boolean) = mutex.withLock {
        check(commitRetainingStoredSecret(preferences.edit().putString("routing_mode", mode.name)
            .putBoolean("routing_local_only", localOnly).putBoolean("routing_tools_required", toolsRequired)))
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun replaceApiKey(apiKey: String) = mutex.withLock {
        require(apiKey.isNotBlank()) { "API key is required." }
        val encrypted = encrypt(apiKey.trim())
        check(
            preferences.edit()
                .putString(KEY_CIPHERTEXT, encrypted.ciphertext)
                .putString(KEY_IV, encrypted.iv)
                .commit(),
        ) { "API key could not be saved." }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun clearApiKey() = mutex.withLock {
        check(preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()) {
            "API key could not be cleared."
        }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun setAutomaticContextCompactionEnabled(enabled: Boolean) = mutex.withLock {
        check(
            commitRetainingStoredSecret(
                preferences.edit().putBoolean(KEY_AUTOMATIC_COMPACTION, enabled),
            ),
        ) {
            "Automatic context compaction setting could not be saved."
        }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun apiKey(): String? = mutex.withLock {
        readSecret(KEY_CIPHERTEXT, KEY_IV)
    }

    override suspend fun openAiApiKey(): String? = mutex.withLock {
        readSecret(KEY_OPENAI_CIPHERTEXT, KEY_OPENAI_IV)
    }

    override suspend fun saveOpenAi(configuration: OpenAiConfiguration, newApiKey: String?) = mutex.withLock {
        val validated = configuration.validated()
        val editor = preferences.edit()
            .putString("openai_model", validated.modelId)
            .putString("openai_profile", validated.profile.name)
            .putBoolean("openai_tools", validated.structuredTools)
        if (!newApiKey.isNullOrBlank()) {
            val encrypted = encrypt(newApiKey.trim())
            editor.putString(KEY_OPENAI_CIPHERTEXT, encrypted.ciphertext)
                .putString(KEY_OPENAI_IV, encrypted.iv)
        }
        check(editor.commit()) { "OpenAI settings could not be saved." }
        mutableSettings.value = loadSnapshot()
    }

    override suspend fun clearOpenAiKey() = mutex.withLock {
        check(preferences.edit().remove(KEY_OPENAI_CIPHERTEXT).remove(KEY_OPENAI_IV).commit())
        mutableSettings.value = loadSnapshot()
    }

    private fun readSecret(ciphertextKey: String, ivKey: String): String? {
        val ciphertext = preferences.getString(ciphertextKey, null)
        val iv = preferences.getString(ivKey, null)
        if (ciphertext == null && iv == null) return null
        if (ciphertext == null || iv == null) throw ProviderCredentialUnavailableException()
        try {
            return decrypt(EncryptedSecret(ciphertext, iv))
        } catch (_: Exception) {
            throw ProviderCredentialUnavailableException()
        }
    }

    private fun loadSnapshot() = ProviderSettingsSnapshot(
        routingMode = preferences.getString("routing_mode", null)?.let {
            runCatching { dev.kinetic.core.model.RoutingMode.valueOf(it) }.getOrNull()
        } ?: dev.kinetic.core.model.RoutingMode.MANUAL,
        routingLocalOnly = preferences.getBoolean("routing_local_only", false),
        routingToolsRequired = preferences.getBoolean("routing_tools_required", false),
        mode = preferences.getString(KEY_MODE, null)
            ?.let { runCatching { ProviderMode.valueOf(it) }.getOrNull() }
            ?: ProviderMode.FAKE,
        cloud = CloudProviderConfiguration(
            displayName = preferences.getString(KEY_DISPLAY_NAME, null)
                ?: "OpenAI-compatible cloud",
            baseUrl = preferences.getString(KEY_BASE_URL, null)
                ?: "https://api.openai.com/v1",
            modelId = preferences.getString(KEY_MODEL_ID, null).orEmpty(),
            requestTimeoutSeconds = preferences.getLong(KEY_TIMEOUT_SECONDS, 45),
            structuredToolCallingEnabled = preferences.getBoolean(KEY_STRUCTURED_TOOLS, false),
        ),
        hasApiKey = preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV),
        automaticContextCompactionEnabled = preferences.getBoolean(KEY_AUTOMATIC_COMPACTION, false),
        openAi = OpenAiConfiguration(
            modelId = preferences.getString("openai_model", null) ?: ASTRA_MODEL,
            profile = preferences.getString("openai_profile", null)
                ?.let { runCatching { ModelProfile.valueOf(it) }.getOrNull() } ?: ModelProfile.FAST,
            structuredTools = preferences.getBoolean("openai_tools", false),
        ),
        hasOpenAiKey = preferences.contains(KEY_OPENAI_CIPHERTEXT) && preferences.contains(KEY_OPENAI_IV),
    )

    private fun commitRetainingStoredSecret(editor: android.content.SharedPreferences.Editor): Boolean {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
        val iv = preferences.getString(KEY_IV, null)
        ciphertext?.let { editor.putString(KEY_CIPHERTEXT, it) }
        iv?.let { editor.putString(KEY_IV, it) }
        if (!editor.commit()) return false
        return preferences.getString(KEY_CIPHERTEXT, null) == ciphertext &&
            preferences.getString(KEY_IV, null) == iv
    }

    private fun encrypt(secret: String): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = secret.toByteArray(Charsets.UTF_8)
        return try {
            EncryptedSecret(
                ciphertext = Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP),
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(secret: EncryptedSecret): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingKey(),
            GCMParameterSpec(128, Base64.decode(secret.iv, Base64.NO_WRAP)),
        )
        val plaintext = cipher.doFinal(Base64.decode(secret.ciphertext, Base64.NO_WRAP))
        return try {
            plaintext.toString(Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw ProviderCredentialUnavailableException()
    }

    private data class EncryptedSecret(val ciphertext: String, val iv: String)

    companion object {
        internal const val PREFERENCES_NAME = "kinetic_provider_settings"
        private const val KEY_MODE = "provider_mode"
        private const val KEY_DISPLAY_NAME = "provider_display_name"
        private const val KEY_BASE_URL = "provider_base_url"
        private const val KEY_MODEL_ID = "provider_model_id"
        private const val KEY_TIMEOUT_SECONDS = "provider_timeout_seconds"
        private const val KEY_STRUCTURED_TOOLS = "provider_structured_tools"
        private const val KEY_AUTOMATIC_COMPACTION = "automatic_context_compaction"
        internal const val KEY_CIPHERTEXT = "provider_api_key_ciphertext"
        internal const val KEY_IV = "provider_api_key_iv"
        internal const val KEY_OPENAI_CIPHERTEXT = "openai_api_key_ciphertext"
        internal const val KEY_OPENAI_IV = "openai_api_key_iv"
        private const val KEY_ALIAS = "kinetic.phase2a.provider_api_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
