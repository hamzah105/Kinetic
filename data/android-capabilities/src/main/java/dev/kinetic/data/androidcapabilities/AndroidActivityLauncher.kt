package dev.kinetic.data.androidcapabilities

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.MAX_CLIPBOARD_TEXT_LENGTH
import dev.kinetic.core.tools.MAX_SHARE_TEXT_LENGTH
import dev.kinetic.core.tools.SettingsDestination
import dev.kinetic.core.tools.isValidEmailComposition
import dev.kinetic.core.tools.isValidHttpsUrl
import dev.kinetic.core.tools.normalizePhoneNumber

sealed interface AndroidCapabilityRequest {
    sealed interface ActivityRequest : AndroidCapabilityRequest

    data class OpenHttpsUrl(val url: String) : ActivityRequest
    data class ShareText(val text: String) : ActivityRequest
    data class OpenSettings(val destination: SettingsDestination) : ActivityRequest
    data class OpenDialer(val phoneNumber: String) : ActivityRequest
    data class ComposeEmail(
        val recipient: String?,
        val subject: String?,
        val body: String?,
    ) : ActivityRequest

    data class CopyTextToClipboard(val text: String) : AndroidCapabilityRequest
}

enum class CapabilityDispatchFailureKind {
    NO_FOREGROUND_ACTIVITY,
    NO_HANDLER,
    BLOCKED_BY_ANDROID,
    INVALID_REQUEST,
    SYSTEM_SERVICE_UNAVAILABLE,
    CONCURRENT_DISPATCH,
}

sealed interface CapabilityDispatchResult {
    data object Dispatched : CapabilityDispatchResult
    data class Failed(val kind: CapabilityDispatchFailureKind) : CapabilityDispatchResult
}

fun interface AndroidCapabilityDispatcher {
    suspend fun availability(
        request: AndroidCapabilityRequest,
    ): AndroidCapabilityAvailability = AndroidCapabilityAvailability.AVAILABLE

    fun dispatch(request: AndroidCapabilityRequest): CapabilityDispatchResult
}

internal object AndroidIntentFactory {
    fun create(request: AndroidCapabilityRequest.ActivityRequest): Intent = when (request) {
        is AndroidCapabilityRequest.OpenHttpsUrl -> {
            require(isValidHttpsUrl(request.url)) { "Only a valid HTTPS URL is allowed." }
            Intent(Intent.ACTION_VIEW, Uri.parse(request.url))
        }
        is AndroidCapabilityRequest.ShareText -> {
            require(request.text.isNotBlank() && request.text.length <= MAX_SHARE_TEXT_LENGTH) {
                "Share text is outside the allowed bounds."
            }
            val sendIntent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, request.text)
            Intent.createChooser(sendIntent, "Share text with")
        }
        is AndroidCapabilityRequest.OpenSettings -> Intent(
            when (request.destination) {
                SettingsDestination.GENERAL -> Settings.ACTION_SETTINGS
                SettingsDestination.WIFI -> Settings.ACTION_WIFI_SETTINGS
            },
        )
        is AndroidCapabilityRequest.OpenDialer -> {
            val normalized = requireNotNull(normalizePhoneNumber(request.phoneNumber)) {
                "The phone number is outside the allowed format."
            }
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", normalized, null))
        }
        is AndroidCapabilityRequest.ComposeEmail -> {
            val input = ComposeEmailInput(request.recipient, request.subject, request.body)
            require(isValidEmailComposition(input)) { "The email composition data is invalid." }
            Intent(
                Intent.ACTION_SENDTO,
                MailtoUriFactory.create(input),
            ).apply {
                request.recipient?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                request.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                request.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
        }
    }
}

/** Builds the RFC 6068 fields Gmail reads for ACTION_SENDTO without exposing arbitrary headers. */
private object MailtoUriFactory {
    private const val RECIPIENT_SAFE_CHARACTERS = "@"

    fun create(input: ComposeEmailInput): Uri {
        val encodedRecipient = Uri.encode(input.recipient.orEmpty(), RECIPIENT_SAFE_CHARACTERS)
        val encodedFields = buildList {
            input.subject?.let { add("subject=${Uri.encode(it)}") }
            input.body?.let { add("body=${Uri.encode(it.withMailtoLineBreaks())}") }
        }
        val encodedSchemeSpecificPart = buildString {
            append(encodedRecipient)
            if (encodedFields.isNotEmpty()) {
                append('?')
                append(encodedFields.joinToString("&"))
            }
        }
        return Uri.Builder()
            .scheme("mailto")
            .encodedOpaquePart(encodedSchemeSpecificPart)
            .build()
    }

    private fun String.withMailtoLineBreaks(): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", "\r\n")
}
