package dev.kinetic.core.policy

import dev.kinetic.core.agent.PermissionFailure
import dev.kinetic.core.tools.ToolDefinition

enum class RiskLevel {
    SAFE,
    CONFIRM,
    SENSITIVE,
    RESTRICTED,
}

enum class DistributionProfile {
    PLAY_CORE,
    LAB,
}

enum class CapabilityCategory {
    EXTERNAL_APPFUNCTION,
    EXTERNAL_MCP,
    DEMONSTRATION,
    APP_INFORMATION,
    EXTERNAL_NAVIGATION,
    USER_MEDIATED_SHARING,
    SYSTEM_SETTINGS,
    CLIPBOARD_WRITE,
    USER_MEDIATED_COMMUNICATION,
}

data class CapabilityMetadata(
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean,
    val requiredPermissions: Set<String> = emptySet(),
    val distributionAvailability: Set<DistributionProfile>,
    val category: CapabilityCategory = CapabilityCategory.DEMONSTRATION,
    val crossesApplicationBoundary: Boolean = false,
) {
    init {
        require(distributionAvailability.isNotEmpty()) {
            "A capability must be available in at least one distribution profile"
        }
    }
}

data class PolicyContext(
    val distributionProfile: DistributionProfile,
    val grantedPermissions: Set<String> = emptySet(),
)

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data object RequireApproval : PolicyDecision
    data class Deny(val error: PermissionFailure) : PolicyDecision
}

fun interface CapabilityPolicy {
    fun evaluate(tool: ToolDefinition, context: PolicyContext): PolicyDecision
}

/** Deterministic policy; model output is never consulted for authorization. */
class DefaultCapabilityPolicy : CapabilityPolicy {
    override fun evaluate(tool: ToolDefinition, context: PolicyContext): PolicyDecision {
        val metadata = tool.capability
        if (context.distributionProfile !in metadata.distributionAvailability) {
            return PolicyDecision.Deny(PermissionFailure(tool.id))
        }
        if (!context.grantedPermissions.containsAll(metadata.requiredPermissions)) {
            return PolicyDecision.Deny(
                PermissionFailure(tool.id, "A required Android permission is not currently granted."),
            )
        }
        if (metadata.riskLevel == RiskLevel.RESTRICTED &&
            context.distributionProfile == DistributionProfile.PLAY_CORE
        ) {
            return PolicyDecision.Deny(PermissionFailure(tool.id))
        }
        return if (metadata.requiresConfirmation || metadata.riskLevel != RiskLevel.SAFE) {
            PolicyDecision.RequireApproval
        } else {
            PolicyDecision.Allow
        }
    }
}
