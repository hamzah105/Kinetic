package dev.kinetic.core.model

enum class RoutingMode { MANUAL, HYBRID }
enum class RouteProvider { LOCAL_LLAMA, OPENAI, CLOUD, FAKE, LOCAL_SIMULATED }
enum class NetworkState { UNKNOWN, AVAILABLE, UNAVAILABLE }
enum class ResourceState { UNKNOWN, NORMAL, CONSTRAINED }
enum class RouteAvailability { UNKNOWN, AVAILABLE, UNAVAILABLE }
enum class RouteReason {
    EXPLICIT_PIN, LOCAL_FIRST, STABLE_PROVIDER_ORDER, NO_ELIGIBLE_PROVIDER, NOT_PINNED_PROVIDER, TEST_BACKEND_EXCLUDED,
    CLOUD_PROHIBITED, NETWORK_UNAVAILABLE, NETWORK_UNKNOWN, PROVIDER_UNAVAILABLE,
    TOOLS_UNSUPPORTED, CONTEXT_TOO_LARGE, LOCAL_RESOURCE_CONSTRAINT,
}

/** Trusted application metadata only. No prompt, memory, model output or credentials. */
data class RoutingRequirements(
    val minimumContextTokens: Int = 0,
    val localOnly: Boolean = false,
    val structuredToolsRequired: Boolean = false,
    val explicitPin: RouteProvider? = null,
) { init { require(minimumContextTokens >= 0) } }

data class RoutingEnvironment(
    val network: NetworkState = NetworkState.UNKNOWN,
    val battery: ResourceState = ResourceState.UNKNOWN,
    val thermal: ResourceState = ResourceState.UNKNOWN,
)

data class RouteCandidate(
    val provider: RouteProvider,
    val networkRequired: Boolean,
    val availability: RouteAvailability,
    val structuredTools: Boolean,
    val maxContextTokens: Int?,
    val testOnly: Boolean = false,
) { init { require(maxContextTokens == null || maxContextTokens > 0) } }

data class CandidateEligibility(val candidate: RouteCandidate, val rejections: List<RouteReason>) {
    val eligible get() = rejections.isEmpty()
}
data class RoutingDecision(
    val mode: RoutingMode,
    val selected: RouteProvider?,
    val reason: RouteReason,
    val requirements: RoutingRequirements,
    val environment: RoutingEnvironment,
    val candidates: List<CandidateEligibility>,
) {
    val cloudProhibited get() = requirements.localOnly
    val localUnavailable get() = candidates.any {
        it.candidate.provider == RouteProvider.LOCAL_LLAMA && it.candidate.availability == RouteAvailability.UNAVAILABLE
    }
}

/** Stable local-first preference, then enum order. No retries or execution authority. */
class HybridModelRouter {
    fun decide(mode: RoutingMode, requirements: RoutingRequirements, environment: RoutingEnvironment,
        candidates: List<RouteCandidate>): RoutingDecision {
        require(mode != RoutingMode.MANUAL || requirements.explicitPin != null)
        require(candidates.map { it.provider }.distinct().size == candidates.size)
        val evaluated = candidates.sortedBy { it.provider.ordinal }.map { candidate ->
            val rejected = buildList {
                if (requirements.explicitPin != null && candidate.provider != requirements.explicitPin) add(RouteReason.NOT_PINNED_PROVIDER)
                if (candidate.testOnly && requirements.explicitPin != candidate.provider) add(RouteReason.TEST_BACKEND_EXCLUDED)
                if (requirements.localOnly && candidate.networkRequired) add(RouteReason.CLOUD_PROHIBITED)
                if (candidate.networkRequired && environment.network == NetworkState.UNAVAILABLE) add(RouteReason.NETWORK_UNAVAILABLE)
                if (mode == RoutingMode.HYBRID && candidate.networkRequired && environment.network == NetworkState.UNKNOWN) add(RouteReason.NETWORK_UNKNOWN)
                if (candidate.availability == RouteAvailability.UNAVAILABLE) add(RouteReason.PROVIDER_UNAVAILABLE)
                if (requirements.structuredToolsRequired && !candidate.structuredTools) add(RouteReason.TOOLS_UNSUPPORTED)
                if (candidate.maxContextTokens != null && requirements.minimumContextTokens > candidate.maxContextTokens) add(RouteReason.CONTEXT_TOO_LARGE)
                if (candidate.provider == RouteProvider.LOCAL_LLAMA &&
                    (environment.battery == ResourceState.CONSTRAINED || environment.thermal == ResourceState.CONSTRAINED)) add(RouteReason.LOCAL_RESOURCE_CONSTRAINT)
            }
            CandidateEligibility(candidate, rejected)
        }
        val selected = evaluated.firstOrNull { it.eligible }?.candidate?.provider
        val reason = when {
            selected == null -> RouteReason.NO_ELIGIBLE_PROVIDER
            requirements.explicitPin != null -> RouteReason.EXPLICIT_PIN
            selected == RouteProvider.LOCAL_LLAMA -> RouteReason.LOCAL_FIRST
            else -> RouteReason.STABLE_PROVIDER_ORDER
        }
        return RoutingDecision(mode, selected, reason,
            requirements, environment, evaluated)
    }
}
