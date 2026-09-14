package dev.kinetic.data.model

import dev.kinetic.core.model.ModelCapabilities
import dev.kinetic.core.model.ModelProfile
import dev.kinetic.core.model.ProviderKind
import dev.kinetic.core.model.ReasoningLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ASTRA_MODEL = "gpt-6-astra"

/** Explicit catalog, not substring heuristics. Image/steering/async adapters are not enabled. */
val OPENAI_MODEL_CAPABILITIES = mapOf(
    ASTRA_MODEL to ModelCapabilities(
        providerKind = ProviderKind.OPENAI_RESPONSES,
        structuredTools = true,
        reasoningLevels = ReasoningLevel.entries.toSet(),
        promptCaching = true,
        maxContextTokens = 1_050_000,
    ),
)

data class OpenAiConfiguration(
    val modelId: String = ASTRA_MODEL,
    val profile: ModelProfile = ModelProfile.FAST,
    val structuredTools: Boolean = false,
) {
    fun validated(): OpenAiConfiguration {
        require(modelId in OPENAI_MODEL_CAPABILITIES) { "Select a supported direct OpenAI model." }
        return this
    }
    fun capabilities(): ModelCapabilities = OPENAI_MODEL_CAPABILITIES.getValue(validated().modelId)
        .copy(structuredTools = structuredTools)
}

data class ProviderMetrics(
    val provider: ProviderKind,
    val modelId: String,
    val profile: ModelProfile?,
    val durationMs: Long,
    val firstTokenMs: Long?,
    val inputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val outputTokens: Long? = null,
    val approximateUsd: Double? = null,
    val outcome: String = "completed",
)

/** Last 20 requests in process memory only. No prompts, IDs, secrets, disk or analytics. */
class LocalProviderMetrics {
    private val values = MutableStateFlow<List<ProviderMetrics>>(emptyList())
    val records = values.asStateFlow()
    @Synchronized fun record(metric: ProviderMetrics) { values.value = (values.value + metric).takeLast(20) }
}

/** Standard tier, USD price snapshot 2026-09-05; unknown usage is not treated as zero. */
internal fun approximateAstraCost(input: Long?, cached: Long?, writes: Long?, output: Long?): Double? {
    if (input == null || cached == null || writes == null || output == null) return null
    if (minOf(input, cached, writes, output) < 0 || cached + writes > input) return null
    val longContext = input > 272_000
    return ((input - cached - writes) * 10.0 + cached + writes * 12.5) *
        (if (longContext) 2 else 1) / 1_000_000 +
        output * 50.0 * (if (longContext) 1.5 else 1.0) / 1_000_000
}
