package dev.kinetic.core.tools

import dev.kinetic.core.agent.ToolFailure
import dev.kinetic.core.policy.CapabilityMetadata
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.RiskLevel
import java.time.Clock

private val allPhaseOneProfiles = setOf(
    DistributionProfile.PLAY_CORE,
    DistributionProfile.LAB,
)

class EchoTool : Tool {
    override val definition = ToolDefinition(
        id = "echo",
        name = "Echo",
        description = "Returns supplied demonstration text without external effects.",
        inputContract = ToolInputContract(ToolInputKind.ECHO_TEXT, "EchoInput(text)"),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false,
            distributionAvailability = allPhaseOneProfiles,
        ),
    )

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val typedInput = input as? EchoInput
            ?: return ToolResult.Failure(
                context.callId,
                definition.id,
                ToolFailure(definition.id, "Echo received invalid typed input."),
            )
        return ToolResult.Success(context.callId, definition.id, EchoOutput(typedInput.text))
    }
}

class CurrentAppTimeTool(private val clock: Clock) : Tool {
    override val definition = ToolDefinition(
        id = "current_app_time",
        name = "Current app time",
        description = "Returns the clock visible to the Kinetic developer shell.",
        inputContract = ToolInputContract(ToolInputKind.NONE, "NoToolInput"),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false,
            distributionAvailability = allPhaseOneProfiles,
        ),
    )

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        if (input !is NoToolInput) {
            return ToolResult.Failure(
                context.callId,
                definition.id,
                ToolFailure(definition.id, "Current app time does not accept input."),
            )
        }
        return ToolResult.Success(
            context.callId,
            definition.id,
            CurrentAppTimeOutput(clock.instant().toString()),
        )
    }
}

class ProtectedDemoTool : Tool {
    override val definition = ToolDefinition(
        id = "protected_demo_tool",
        name = "Protected demo action",
        description = "Performs a harmless action that demonstrates mandatory user approval.",
        inputContract = ToolInputContract(
            ToolInputKind.PROTECTED_ACTION,
            "ProtectedDemoInput(action)",
        ),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.CONFIRM,
            requiresConfirmation = true,
            distributionAvailability = allPhaseOneProfiles,
        ),
    )

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        if (input !is ProtectedDemoInput) {
            return ToolResult.Failure(
                context.callId,
                definition.id,
                ToolFailure(definition.id, "Protected demo received invalid typed input."),
            )
        }
        return ToolResult.Success(
            context.callId,
            definition.id,
            ProtectedDemoOutput("Protected demo action completed."),
        )
    }
}

class FailingDemoTool : Tool {
    override val definition = ToolDefinition(
        id = "failing_demo_tool",
        name = "Failing demo",
        description = "Returns a deterministic typed failure for developer testing.",
        inputContract = ToolInputContract(ToolInputKind.NONE, "NoToolInput"),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.SAFE,
            requiresConfirmation = false,
            distributionAvailability = allPhaseOneProfiles,
        ),
    )

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult =
        ToolResult.Failure(
            context.callId,
            definition.id,
            ToolFailure(definition.id, "The deterministic failing demo tool failed."),
        )
}

object DemoToolCatalog {
    fun create(clock: Clock): List<Tool> = listOf(
        EchoTool(),
        CurrentAppTimeTool(clock),
        ProtectedDemoTool(),
        FailingDemoTool(),
    )
}

