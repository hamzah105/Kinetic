package dev.kinetic.data.androidcapabilities

import dev.kinetic.core.agent.ToolFailure
import dev.kinetic.core.policy.CapabilityCategory
import dev.kinetic.core.policy.CapabilityMetadata
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.RiskLevel
import dev.kinetic.core.tools.AndroidCapabilityOutput
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.OpenDialerInput
import dev.kinetic.core.tools.OpenHttpsUrlInput
import dev.kinetic.core.tools.OpenSettingsInput
import dev.kinetic.core.tools.ShareTextInput
import dev.kinetic.core.tools.Tool
import dev.kinetic.core.tools.ToolAvailability
import dev.kinetic.core.tools.ToolAvailabilityStatus
import dev.kinetic.core.tools.ToolDefinition
import dev.kinetic.core.tools.ToolExecutionContext
import dev.kinetic.core.tools.ToolInput
import dev.kinetic.core.tools.ToolInputContract
import dev.kinetic.core.tools.ToolInputKind
import dev.kinetic.core.tools.ToolResult

private val playAndLab = setOf(DistributionProfile.PLAY_CORE, DistributionProfile.LAB)

private fun metadata(category: CapabilityCategory) = CapabilityMetadata(
    riskLevel = RiskLevel.CONFIRM,
    requiresConfirmation = true,
    requiredPermissions = emptySet(),
    distributionAvailability = playAndLab,
    category = category,
    crossesApplicationBoundary = true,
)

abstract class AndroidCapabilityTool(
    final override val definition: ToolDefinition,
    private val dispatcher: AndroidCapabilityDispatcher,
) : Tool {
    protected abstract fun requestFor(input: ToolInput): AndroidCapabilityRequest?

    final override suspend fun checkAvailability(
        input: ToolInput,
        context: ToolExecutionContext,
    ): ToolAvailability {
        val request = requestFor(input) ?: return ToolAvailability.Unavailable(
            ToolAvailabilityStatus.UNAVAILABLE_PRECONDITION,
            "The Android capability request is invalid.",
        )
        return dispatcher.availability(request).toToolAvailability()
    }

    protected fun dispatch(
        request: AndroidCapabilityRequest,
        context: ToolExecutionContext,
        successMessage: String,
    ): ToolResult = when (val result = dispatcher.dispatch(request)) {
        CapabilityDispatchResult.Dispatched -> ToolResult.Success(
            context.callId,
            definition.id,
            AndroidCapabilityOutput(successMessage),
        )
        is CapabilityDispatchResult.Failed -> ToolResult.Failure(
            context.callId,
            definition.id,
            ToolFailure(definition.id, result.kind.safeMessage()),
        )
    }
}

class OpenHttpsUrlTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "open_https_url",
        name = "Open HTTPS URL",
        description = "Opens one validated HTTPS webpage using Android's normal activity resolver.",
        inputContract = ToolInputContract(ToolInputKind.HTTPS_URL, "OpenHttpsUrlInput(url)"),
        capability = metadata(CapabilityCategory.EXTERNAL_NAVIGATION),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? OpenHttpsUrlInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.OpenHttpsUrl(it.url) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val request = requestFor(input)
            ?: return invalidInput(context, "A valid HTTPS URL is required.")
        return dispatch(
            request,
            context,
            "HTTPS URL opened through Android.",
        )
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

class ShareTextTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "share_text",
        name = "Share text",
        description = "Opens Android's chooser so the user can select whether and where to share text.",
        inputContract = ToolInputContract(ToolInputKind.SHARE_TEXT, "ShareTextInput(text)"),
        capability = metadata(CapabilityCategory.USER_MEDIATED_SHARING),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? ShareTextInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.ShareText(it.text) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val request = requestFor(input)
            ?: return invalidInput(context, "Non-empty share text within the local limit is required.")
        return dispatch(
            request,
            context,
            "Android share chooser opened.",
        )
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

class OpenSettingsTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "open_settings",
        name = "Open Settings",
        description = "Opens one allowlisted Android Settings destination without changing settings.",
        inputContract = ToolInputContract(
            ToolInputKind.SETTINGS_DESTINATION,
            "OpenSettingsInput(destination=GENERAL|WIFI)",
        ),
        capability = metadata(CapabilityCategory.SYSTEM_SETTINGS),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? OpenSettingsInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.OpenSettings(it.destination) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val typed = input as? OpenSettingsInput
            ?: return invalidInput(context, "An allowlisted Settings destination is required.")
        if (!definition.inputContract.accepts(typed)) {
            return invalidInput(context, "An allowlisted Settings destination is required.")
        }
        val success = when (typed.destination) {
            dev.kinetic.core.tools.SettingsDestination.GENERAL -> "Android Settings opened."
            dev.kinetic.core.tools.SettingsDestination.WIFI -> "Wi-Fi settings opened."
        }
        return dispatch(requireNotNull(requestFor(typed)), context, success)
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

class CopyTextToClipboardTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "copy_text_to_clipboard",
        name = "Copy text to clipboard",
        description = "Writes bounded text to Android's clipboard after explicit confirmation.",
        inputContract = ToolInputContract(
            ToolInputKind.CLIPBOARD_TEXT,
            "CopyTextToClipboardInput(text)",
        ),
        capability = metadata(CapabilityCategory.CLIPBOARD_WRITE),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? CopyTextToClipboardInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.CopyTextToClipboard(it.text) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val request = requestFor(input)
            ?: return invalidInput(context, "Non-empty clipboard text within the local limit is required.")
        return dispatch(
            request,
            context,
            "Text copied to the Android clipboard.",
        )
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

class OpenDialerTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "open_dialer",
        name = "Open dialer",
        description = "Opens Android's dialer with a validated number without placing a call.",
        inputContract = ToolInputContract(ToolInputKind.PHONE_NUMBER, "OpenDialerInput(phoneNumber)"),
        capability = metadata(CapabilityCategory.USER_MEDIATED_COMMUNICATION),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? OpenDialerInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.OpenDialer(it.phoneNumber) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val request = requestFor(input)
            ?: return invalidInput(context, "A valid phone number is required.")
        return dispatch(
            request,
            context,
            "Dialer opened with the requested number.",
        )
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

class ComposeEmailTool(dispatcher: AndroidCapabilityDispatcher) : AndroidCapabilityTool(
    definition = ToolDefinition(
        id = "compose_email",
        name = "Compose email",
        description = "Opens a user-controlled email composer with bounded optional fields.",
        inputContract = ToolInputContract(
            ToolInputKind.EMAIL_COMPOSITION,
            "ComposeEmailInput(recipient?, subject?, body?)",
        ),
        capability = metadata(CapabilityCategory.USER_MEDIATED_COMMUNICATION),
    ),
    dispatcher = dispatcher,
) {
    override fun requestFor(input: ToolInput): AndroidCapabilityRequest? =
        (input as? ComposeEmailInput)
            ?.takeIf(definition.inputContract::accepts)
            ?.let { AndroidCapabilityRequest.ComposeEmail(it.recipient, it.subject, it.body) }

    override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult {
        val request = requestFor(input)
            ?: return invalidInput(context, "Valid bounded email composition data is required.")
        return dispatch(
            request,
            context,
            "Email composer opened.",
        )
    }

    private fun invalidInput(context: ToolExecutionContext, message: String) = ToolResult.Failure(
        context.callId,
        definition.id,
        ToolFailure(definition.id, message),
    )
}

object AndroidCapabilityToolCatalog {
    fun create(dispatcher: AndroidCapabilityDispatcher): List<Tool> = listOf(
        OpenHttpsUrlTool(dispatcher),
        ShareTextTool(dispatcher),
        OpenSettingsTool(dispatcher),
        CopyTextToClipboardTool(dispatcher),
        OpenDialerTool(dispatcher),
        ComposeEmailTool(dispatcher),
    )
}

private fun CapabilityDispatchFailureKind.safeMessage(): String = when (this) {
    CapabilityDispatchFailureKind.NO_FOREGROUND_ACTIVITY ->
        "Kinetic must be open to perform this Android action."
    CapabilityDispatchFailureKind.NO_HANDLER ->
        "Android could not find an application that can handle this request."
    CapabilityDispatchFailureKind.BLOCKED_BY_ANDROID ->
        "Android blocked this request."
    CapabilityDispatchFailureKind.INVALID_REQUEST ->
        "The Android capability request was invalid."
    CapabilityDispatchFailureKind.SYSTEM_SERVICE_UNAVAILABLE ->
        "The required Android system service is unavailable."
    CapabilityDispatchFailureKind.CONCURRENT_DISPATCH ->
        "Another Android action is already being dispatched."
}

private fun AndroidCapabilityAvailability.toToolAvailability(): ToolAvailability = when (this) {
    AndroidCapabilityAvailability.AVAILABLE -> ToolAvailability.Available
    AndroidCapabilityAvailability.UNAVAILABLE_NO_FOREGROUND_ACTIVITY ->
        ToolAvailability.Unavailable(
            ToolAvailabilityStatus.UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
            "Kinetic must be open to perform this Android action.",
        )
    AndroidCapabilityAvailability.UNAVAILABLE_UNSUPPORTED_OS -> ToolAvailability.Unavailable(
        ToolAvailabilityStatus.UNAVAILABLE_UNSUPPORTED_OS,
        "This Android action is not supported on this device.",
    )
    AndroidCapabilityAvailability.UNAVAILABLE_NO_HANDLER -> ToolAvailability.Unavailable(
        ToolAvailabilityStatus.UNAVAILABLE_NO_HANDLER,
        "Android could not find an application that can handle this request.",
    )
    AndroidCapabilityAvailability.UNAVAILABLE_PRECONDITION -> ToolAvailability.Unavailable(
        ToolAvailabilityStatus.UNAVAILABLE_PRECONDITION,
        "This Android action is not currently available.",
    )
    AndroidCapabilityAvailability.UNAVAILABLE_CONCURRENT_EXECUTION -> ToolAvailability.Unavailable(
        ToolAvailabilityStatus.UNAVAILABLE_CONCURRENT_EXECUTION,
        "Another Android action is already being dispatched.",
    )
}
