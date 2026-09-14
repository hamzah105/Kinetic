package dev.kinetic.core.model

import dev.kinetic.core.tools.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single

enum class LocalRuntimeFamily { SIMULATED_TEST, AICORE, LITERT_LM, LLAMA_CPP, LLAMATIK }
enum class LocalInferenceAvailability {
    AVAILABLE, UNKNOWN, MODEL_NOT_INSTALLED, DEVICE_UNSUPPORTED, RUNTIME_UNAVAILABLE,
    INSUFFICIENT_MEMORY, INCOMPATIBLE_ABI, INITIALIZING, ERROR,
}
enum class LocalInputModality { TEXT, IMAGE, AUDIO }
enum class LocalAcceleration { CPU, GPU, NPU }

/** Unknown numeric facts are null, not zero; capabilities describe an adapter, not authority. */
data class LocalModelDescriptor(
    val runtime: LocalRuntimeFamily,
    val modelId: String,
    val version: String,
    val approximateBytes: Long? = null,
    val quantization: String? = null,
    val inputModalities: Set<LocalInputModality> = setOf(LocalInputModality.TEXT),
    val maxContextTokens: Int? = null,
    val structuredOutput: Boolean = false,
    val toolProposals: Boolean = false,
    val streaming: Boolean = false,
    val requiredAbis: Set<String> = emptySet(),
    val requiredAcceleration: Set<LocalAcceleration> = emptySet(),
    val minimumRecommendedRamBytes: Long? = null,
    val availability: LocalInferenceAvailability = LocalInferenceAvailability.UNKNOWN,
) {
    init {
        require(modelId.isNotBlank() && version.isNotBlank())
        require(approximateBytes == null || approximateBytes > 0)
        require(maxContextTokens == null || maxContextTokens > 0)
        require(minimumRecommendedRamBytes == null || minimumRecommendedRamBytes > 0)
        require(inputModalities.isNotEmpty())
    }
}

/** No Context, HTTP, persistence, policy, approval or execution dependency belongs in this port. */
interface LocalModelProvider : ModelProvider {
    val descriptor: LocalModelDescriptor
    fun availability(): LocalInferenceAvailability = descriptor.availability
    override fun capabilities() = ModelCapabilities(
        ProviderKind.LOCAL, streaming = descriptor.streaming,
        structuredTools = descriptor.toolProposals,
        imageInput = LocalInputModality.IMAGE in descriptor.inputModalities,
        maxContextTokens = descriptor.maxContextTokens,
    )
}

data class LocalDeviceFacts(
    val apiLevel: Int,
    val supportedAbis: Set<String>,
    val physicalRamBytes: Long?,
    val memoryClassMiB: Int?,
    val availableStorageBytes: Long?,
    val emulatorLikely: Boolean,
) {
    init {
        require(apiLevel > 0)
        require(physicalRamBytes == null || physicalRamBytes > 0)
        require(memoryClassMiB == null || memoryClassMiB > 0)
        require(availableStorageBytes == null || availableStorageBytes >= 0)
    }
}

/** Reports only known constraints. No installed-model, accelerator or AICore guesses. */
fun localCompatibility(facts: LocalDeviceFacts, model: LocalModelDescriptor): LocalInferenceAvailability {
    if (model.requiredAbis.isNotEmpty() && facts.supportedAbis.isNotEmpty() &&
        model.requiredAbis.intersect(facts.supportedAbis).isEmpty()) {
        return LocalInferenceAvailability.INCOMPATIBLE_ABI
    }
    if (model.minimumRecommendedRamBytes != null && facts.physicalRamBytes != null &&
        facts.physicalRamBytes < model.minimumRecommendedRamBytes) {
        return LocalInferenceAvailability.INSUFFICIENT_MEMORY
    }
    return model.availability
}

/** Scripted development inference, NOT a local LLM and NOT a quality/performance benchmark. */
class SimulatedLocalModelProvider(
    private val chunkDelayMillis: Long = 80,
    private val currentAvailability: () -> LocalInferenceAvailability = { LocalInferenceAvailability.AVAILABLE },
) : LocalModelProvider {
    init { require(chunkDelayMillis >= 0) }
    override val providerId = "local-simulated-test"
    override val descriptor = LocalModelDescriptor(
        LocalRuntimeFamily.SIMULATED_TEST, "kinetic-simulated-test", "1",
        maxContextTokens = 4_096, structuredOutput = true, toolProposals = true, streaming = true,
        availability = LocalInferenceAvailability.AVAILABLE,
    )
    override fun availability() = currentAvailability()
    override fun toolSupport() = ModelToolSupport.Structured(setOf("echo", "protected_demo_tool", "open_https_url"))
    override suspend fun generate(request: ModelRequest) = stream(request)
        .filterIsInstance<ModelStreamEvent.Completed>().single().response

    override fun stream(request: ModelRequest) = flow {
        checkAvailable()
        val limit = requireNotNull(descriptor.maxContextTokens)
        val estimated = request.contextPlan?.totalEstimatedTokens ?: (
            request.messages.sumOf { dev.kinetic.core.context.estimateTokens(it.content) + 8 } +
                request.memoryContext.ordered.sumOf { dev.kinetic.core.context.estimateTokens(it.content) + 16 } +
                (request.sessionSummary?.let { dev.kinetic.core.context.estimateTokens(it.content) + 24 } ?: 0) + 1_536)
        if (estimated > limit) {
            throw ModelProviderException(ModelProviderFailureKind.CONTEXT_LIMIT, "Local context exceeds its configured limit.")
        }
        emit(ModelStreamEvent.Started)
        val prompt = request.messages.lastOrNull()?.content.orEmpty().trim()
        val call = when {
            prompt.equals("local unavailable", true) -> throw ModelProviderException(
                ModelProviderFailureKind.LOCAL_UNAVAILABLE, "Simulated local status: MODEL_NOT_INSTALLED.")
            prompt.equals("local malformed", true) -> {
                // Deliberately truncated test stream. Kernel must fail closed; no action is emitted.
                emit(ModelStreamEvent.TextDelta("SIMULATED / TEST: incomplete response"))
                return@flow
            }
            prompt.equals("protected demo", true) -> ToolCall(
                "local-${request.turnId}", "protected_demo_tool", ProtectedDemoInput("simulated local demo"),
                proposalTurnId = request.turnId)
            prompt.startsWith("open https://") -> ToolCall(
                "local-${request.turnId}", "open_https_url", OpenHttpsUrlInput(prompt.removePrefix("open ")),
                proposalTurnId = request.turnId)
            prompt.startsWith("echo:") -> ToolCall(
                "local-${request.turnId}", "echo", EchoInput(prompt.substringAfter(':').trim()),
                proposalTurnId = request.turnId)
            else -> null
        }
        val content = when {
            call != null -> "SIMULATED / TEST: proposing ${call.toolId}. Kinetic decides validation and approval."
            prompt.equals("local json", true) -> "{\"mode\":\"SIMULATED_TEST\",\"ok\":true}"
            else -> "SIMULATED / TEST: local response generated without a network request. No real model is installed."
        }
        for (chunk in content.chunked(12)) { delay(chunkDelayMillis); emit(ModelStreamEvent.TextDelta(chunk)) }
        call?.let { emit(ModelStreamEvent.ToolCallCompleted(it)) }
        emit(ModelStreamEvent.Completed(ModelResponse("local-response-${request.turnId}", content, listOfNotNull(call))))
    }

    override fun streamContinuation(request: ModelContinuationRequest) = flow {
        checkAvailable()
        val call = request.proposal.toolCalls.singleOrNull()
        if (call == null || call.callId != request.toolResult.callId || call.toolId != request.toolResult.toolId) {
            throw ModelProviderException(ModelProviderFailureKind.MALFORMED_RESPONSE, "Local result identity mismatch.")
        }
        val result = when (val value = request.toolResult) {
            is ToolResult.Success -> value.output.displayText()
            is ToolResult.Failure -> value.error.userMessage
        }
        val content = "SIMULATED / TEST: Kinetic tool result: ${result.take(800)}"
        emit(ModelStreamEvent.Started)
        for (chunk in content.chunked(12)) { delay(chunkDelayMillis); emit(ModelStreamEvent.TextDelta(chunk)) }
        emit(ModelStreamEvent.Completed(ModelResponse("local-continuation-${request.originalRequest.turnId}", content)))
    }

    private fun checkAvailable() {
        val status = availability()
        if (status != LocalInferenceAvailability.AVAILABLE) throw ModelProviderException(
            ModelProviderFailureKind.LOCAL_UNAVAILABLE, "Local inference unavailable: ${status.name}.")
    }
}
