package dev.kinetic.core.tools

import dev.kinetic.core.agent.ToolFailure
import dev.kinetic.core.policy.CapabilityMetadata
import java.net.URI
import java.security.MessageDigest

enum class ToolInputKind {
    APPFUNCTION_JSON,
    MCP_JSON,
    NONE,
    ECHO_TEXT,
    PROTECTED_ACTION,
    HTTPS_URL,
    SHARE_TEXT,
    SETTINGS_DESTINATION,
    CLIPBOARD_TEXT,
    PHONE_NUMBER,
    EMAIL_COMPOSITION,
}

sealed interface ToolInput {
    val kind: ToolInputKind
    fun journalSummary(): String
    fun approvalSummary(): String = journalSummary()

    /** Stable, non-reversible binding used to authorize exactly these validated arguments. */
    fun authorizationBinding(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalAuthorizationValue().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun canonicalAuthorizationValue(): String
}

data class AppFunctionToolInput(val canonicalJson: String) : ToolInput {
    init { require(canonicalJson.length <= 8192) }
    override val kind = ToolInputKind.APPFUNCTION_JSON
    override fun journalSummary() = "appFunctionArgumentsLength=${canonicalJson.length}"
    override fun approvalSummary() = "External AppFunction arguments (untrusted data): $canonicalJson"
    override fun canonicalAuthorizationValue() = "${kind.name}\u0000$canonicalJson"
}

/** Opaque bounded data; the registered adapter owns strict schema validation, never execution text parsing. */
data class McpToolInput(val canonicalJson: String) : ToolInput {
    init { require(canonicalJson.length <= 8192) }
    override val kind = ToolInputKind.MCP_JSON
    override fun journalSummary() = "externalArgumentsLength=${canonicalJson.length}"
    override fun approvalSummary() = "External MCP arguments (untrusted data): $canonicalJson"
    override fun canonicalAuthorizationValue() = "${kind.name}\u0000$canonicalJson"
}

data object NoToolInput : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.NONE
    override fun journalSummary(): String = "no input"
    override fun canonicalAuthorizationValue(): String = kind.name
}

data class EchoInput(val text: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.ECHO_TEXT
    override fun journalSummary(): String = "textLength=${text.length}"
    override fun approvalSummary(): String = "Text: ${text.safePreview()}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$text"
}

data class ProtectedDemoInput(val action: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.PROTECTED_ACTION
    override fun journalSummary(): String = "protected demo action"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$action"
}

data class OpenHttpsUrlInput(val url: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.HTTPS_URL
    override fun journalSummary(): String = "httpsUrlLength=${url.length}"
    override fun approvalSummary(): String = "URL: ${url.safePreview(MAX_APPROVAL_URL_PREVIEW)}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$url"
}

data class ShareTextInput(val text: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.SHARE_TEXT
    override fun journalSummary(): String = "shareTextLength=${text.length}"
    override fun approvalSummary(): String = "Text preview: ${text.safePreview()}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$text"
}

enum class SettingsDestination {
    GENERAL,
    WIFI,
}

data class OpenSettingsInput(val destination: SettingsDestination) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.SETTINGS_DESTINATION
    override fun journalSummary(): String = "settingsDestination=${destination.name}"
    override fun approvalSummary(): String = "Destination: ${destination.name}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000${destination.name}"
}

data class CopyTextToClipboardInput(val text: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.CLIPBOARD_TEXT
    override fun journalSummary(): String = "clipboardTextLength=${text.length}"
    override fun approvalSummary(): String = "Text preview: ${text.safePreview()}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$text"
}

data class OpenDialerInput(val phoneNumber: String) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.PHONE_NUMBER
    override fun journalSummary(): String =
        "phoneDigits=${normalizePhoneNumber(phoneNumber)?.count(Char::isDigit) ?: 0}"
    override fun approvalSummary(): String = "Number: ${phoneNumber.safePhonePreview()}"
    override fun canonicalAuthorizationValue(): String = "${kind.name}\u0000$phoneNumber"
}

data class ComposeEmailInput(
    val recipient: String? = null,
    val subject: String? = null,
    val body: String? = null,
) : ToolInput {
    override val kind: ToolInputKind = ToolInputKind.EMAIL_COMPOSITION
    override fun journalSummary(): String = buildString {
        append("emailRecipientPresent=${recipient != null}")
        append(",subjectLength=${subject?.length ?: 0}")
        append(",bodyLength=${body?.length ?: 0}")
    }

    override fun approvalSummary(): String = buildList {
        recipient?.let { add("Recipient: ${it.safePreview(MAX_EMAIL_RECIPIENT_LENGTH)}") }
        subject?.let { add("Subject: ${it.safePreview()}") }
        body?.let { add("Body preview: ${it.safePreview()}") }
    }.joinToString("\n")

    override fun canonicalAuthorizationValue(): String = buildString {
        append(kind.name)
        append('\u0000')
        append(recipient.canonicalNullableValue())
        append('\u0000')
        append(subject.canonicalNullableValue())
        append('\u0000')
        append(body.canonicalNullableValue())
    }
}

data class ToolInputContract(
    val kind: ToolInputKind,
    val description: String,
    val jsonSchema: String? = null,
    val validateJson: ((String) -> Boolean)? = null,
) {
    fun accepts(input: ToolInput): Boolean = when {
        input.kind != kind -> false
        input is McpToolInput -> validateJson?.invoke(input.canonicalJson) == true
        input is AppFunctionToolInput -> validateJson?.invoke(input.canonicalJson) == true
        input is EchoInput -> input.text.length <= MAX_TOOL_TEXT_LENGTH
        input is ProtectedDemoInput -> input.action.length <= MAX_TOOL_TEXT_LENGTH
        input is OpenHttpsUrlInput -> isValidHttpsUrl(input.url)
        input is ShareTextInput -> input.text.isNotBlank() && input.text.length <= MAX_SHARE_TEXT_LENGTH
        input is OpenSettingsInput -> input.destination in SettingsDestination.entries
        input is CopyTextToClipboardInput ->
            input.text.isNotBlank() && input.text.length <= MAX_CLIPBOARD_TEXT_LENGTH
        input is OpenDialerInput -> normalizePhoneNumber(input.phoneNumber) != null
        input is ComposeEmailInput -> isValidEmailComposition(input)
        else -> true
    }
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val inputContract: ToolInputContract,
    val capability: CapabilityMetadata,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) { "Tool id must be stable snake_case" }
        require(name.isNotBlank()) { "Tool name must not be blank" }
        require(description.isNotBlank()) { "Tool description must not be blank" }
    }
}

data class ToolCall(
    val callId: String,
    val toolId: String,
    val input: ToolInput,
    val proposalTurnId: String? = null,
    val index: Int = 0,
) {
    init {
        require(callId.isNotBlank() && callId.length <= 200) { "Tool call id is invalid" }
        require(toolId.matches(Regex("[a-z][a-z0-9_]*"))) { "Tool id is invalid" }
        require(index >= 0) { "Tool call index is invalid" }
    }
}

const val MAX_TOOL_TEXT_LENGTH = 2_000
const val MAX_HTTPS_URL_LENGTH = 2_048
const val MAX_SHARE_TEXT_LENGTH = 4_000
const val MAX_CLIPBOARD_TEXT_LENGTH = 4_000
const val MAX_PHONE_NUMBER_LENGTH = 32
const val MAX_EMAIL_RECIPIENT_LENGTH = 254
const val MAX_EMAIL_SUBJECT_LENGTH = 200
const val MAX_EMAIL_BODY_LENGTH = 4_000
private const val MAX_APPROVAL_PREVIEW = 160
private const val MAX_APPROVAL_URL_PREVIEW = 512

fun isValidHttpsUrl(value: String): Boolean {
    if (value.isBlank() || value.length > MAX_HTTPS_URL_LENGTH) return false
    if (!value.startsWith("https://")) return false
    if (value.any { it.isWhitespace() || it.isISOControl() } || '\\' in value) return false
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return false
    }
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return false
    if (uri.port != -1 && uri.port !in 1..65_535) return false
    return uri.rawAuthority?.isNotBlank() == true
}

fun normalizePhoneNumber(value: String): String? {
    if (value.isEmpty() || value.length > MAX_PHONE_NUMBER_LENGTH || value != value.trim()) return null
    if (value.any(Char::isISOControl)) return null
    if (!value.matches(Regex("^\\+?[0-9]+(?:[ -][0-9]+)*$"))) return null
    val normalized = value.replace(" ", "").replace("-", "")
    val digits = normalized.count(Char::isDigit)
    if (digits !in 1..15) return null
    return normalized
}

fun isValidEmailRecipient(value: String): Boolean {
    if (value.isEmpty() || value.length > MAX_EMAIL_RECIPIENT_LENGTH || value != value.trim()) return false
    if (value.any { it.isWhitespace() || it.isISOControl() }) return false
    return value.matches(
        Regex(
            "^[A-Za-z0-9.!#$%&'*+/=^_`{|}~-]+@" +
                "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
                "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
        ),
    )
}

fun isValidEmailComposition(input: ComposeEmailInput): Boolean {
    if (input.recipient == null && input.subject == null && input.body == null) return false
    if (input.recipient != null && !isValidEmailRecipient(input.recipient)) return false
    if (input.subject != null && (
            input.subject.isBlank() ||
                input.subject.length > MAX_EMAIL_SUBJECT_LENGTH ||
                input.subject.any(Char::isISOControl)
            )
    ) return false
    if (input.body != null && (
            input.body.isBlank() ||
                input.body.length > MAX_EMAIL_BODY_LENGTH ||
                input.body.any { it == '\u0000' }
            )
    ) return false
    return true
}

private fun String.safePreview(maxLength: Int = MAX_APPROVAL_PREVIEW): String {
    val singleLine = replace(Regex("[\\r\\n\\t]+"), " ").trim()
    return if (singleLine.length <= maxLength) singleLine else singleLine.take(maxLength) + "..."
}

private fun String.safePhonePreview(): String {
    val normalized = normalizePhoneNumber(this) ?: return "invalid number"
    return if (normalized.length <= 8) normalized else normalized.take(5) + "..." + normalized.takeLast(3)
}

private fun String?.canonicalNullableValue(): String =
    this?.let { "${it.length}:$it" } ?: "-1:"

data class ToolExecutionContext(
    val sessionId: String,
    val turnId: String,
    val callId: String,
)

/** Local, provider-neutral result of a side-effect precondition check. */
enum class ToolAvailabilityStatus {
    AVAILABLE,
    UNAVAILABLE_NO_FOREGROUND_ACTIVITY,
    UNAVAILABLE_UNSUPPORTED_OS,
    UNAVAILABLE_NO_HANDLER,
    UNAVAILABLE_PRECONDITION,
    UNAVAILABLE_CONCURRENT_EXECUTION,
}

sealed interface ToolAvailability {
    val status: ToolAvailabilityStatus

    data object Available : ToolAvailability {
        override val status: ToolAvailabilityStatus = ToolAvailabilityStatus.AVAILABLE
    }

    data class Unavailable(
        override val status: ToolAvailabilityStatus,
        val userMessage: String,
    ) : ToolAvailability {
        init {
            require(status != ToolAvailabilityStatus.AVAILABLE)
            require(userMessage.isNotBlank() && userMessage.length <= 200)
            require(userMessage.none(Char::isISOControl))
        }
    }
}

sealed interface ToolOutput {
    fun displayText(): String
}

data class EchoOutput(val text: String) : ToolOutput {
    override fun displayText(): String = text
}

data class CurrentAppTimeOutput(val isoInstant: String) : ToolOutput {
    override fun displayText(): String = isoInstant
}

data class ProtectedDemoOutput(val message: String) : ToolOutput {
    override fun displayText(): String = message
}

data class AndroidCapabilityOutput(val message: String) : ToolOutput {
    override fun displayText(): String = message
}

/** Sanitized durable output reconstructed for a completed turn after process restart. */
data class RecoveredToolOutput(val text: String) : ToolOutput {
    override fun displayText(): String = text
}

sealed interface ToolResult {
    val callId: String
    val toolId: String

    data class Success(
        override val callId: String,
        override val toolId: String,
        val output: ToolOutput,
    ) : ToolResult

    data class Failure(
        override val callId: String,
        override val toolId: String,
        val error: ToolFailure,
    ) : ToolResult
}

interface Tool {
    val definition: ToolDefinition

    /** Must inspect only local preconditions and must not perform the tool's side effect. */
    suspend fun checkAvailability(
        input: ToolInput,
        context: ToolExecutionContext,
    ): ToolAvailability = ToolAvailability.Available

    suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult
}
