package dev.kinetic.core

import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.policy.CapabilityMetadata
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.RiskLevel
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.session.InMemoryRunLedger
import dev.kinetic.core.tools.MAX_HTTPS_URL_LENGTH
import dev.kinetic.core.tools.MAX_SHARE_TEXT_LENGTH
import dev.kinetic.core.tools.MAX_CLIPBOARD_TEXT_LENGTH
import dev.kinetic.core.tools.MAX_EMAIL_BODY_LENGTH
import dev.kinetic.core.tools.MAX_EMAIL_SUBJECT_LENGTH
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.OpenHttpsUrlInput
import dev.kinetic.core.tools.OpenDialerInput
import dev.kinetic.core.tools.OpenSettingsInput
import dev.kinetic.core.tools.SettingsDestination
import dev.kinetic.core.tools.ShareTextInput
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolInputContract
import dev.kinetic.core.tools.ToolInputKind
import dev.kinetic.core.tools.ToolInput
import dev.kinetic.core.tools.isValidHttpsUrl
import dev.kinetic.core.tools.isValidEmailRecipient
import dev.kinetic.core.tools.normalizePhoneNumber
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AndroidCapabilityContractsTest {
    @Test
    fun `only bounded well formed lowercase HTTPS URLs are accepted`() {
        assertTrue(isValidHttpsUrl("https://example.com/path?q=1#section"))
        listOf(
            "",
            "http://example.com",
            "file:///tmp/value",
            "content://provider/value",
            "intent://example.com",
            "javascript:alert(1)",
            "data:text/plain,value",
            "market://details?id=app",
            "custom://example.com",
            "example.com",
            "https://",
            "https://user:password@example.com",
            "https://example.com\\redirect",
            "HTTPS://example.com",
            "https://exa mple.com",
            "https://example.com/" + "x".repeat(MAX_HTTPS_URL_LENGTH),
        ).forEach { assertFalse(isValidHttpsUrl(it), it) }
    }

    @Test
    fun `share and settings contracts remain narrow`() {
        val share = ToolInputContract(ToolInputKind.SHARE_TEXT, "share")
        val settings = ToolInputContract(ToolInputKind.SETTINGS_DESTINATION, "settings")

        assertTrue(share.accepts(ShareTextInput("KINETIC_SHARE_OK")))
        assertFalse(share.accepts(ShareTextInput("")))
        assertFalse(share.accepts(ShareTextInput(" ")))
        assertFalse(share.accepts(ShareTextInput("x".repeat(MAX_SHARE_TEXT_LENGTH + 1))))
        assertTrue(settings.accepts(OpenSettingsInput(SettingsDestination.GENERAL)))
        assertTrue(settings.accepts(OpenSettingsInput(SettingsDestination.WIFI)))
        assertFalse(settings.accepts(ShareTextInput("android.settings.ACCESSIBILITY_SETTINGS")))
    }

    @Test
    fun `Phase 3B inputs are bounded conservative and injection resistant`() {
        val clipboard = ToolInputContract(ToolInputKind.CLIPBOARD_TEXT, "clipboard")
        assertTrue(clipboard.accepts(CopyTextToClipboardInput("KINETIC_CLIPBOARD_OK")))
        assertFalse(clipboard.accepts(CopyTextToClipboardInput("")))
        assertFalse(clipboard.accepts(CopyTextToClipboardInput(" ")))
        assertFalse(
            clipboard.accepts(
                CopyTextToClipboardInput("x".repeat(MAX_CLIPBOARD_TEXT_LENGTH + 1)),
            ),
        )

        val dialer = ToolInputContract(ToolInputKind.PHONE_NUMBER, "dialer")
        assertTrue(dialer.accepts(OpenDialerInput("+923001234567")))
        assertTrue(dialer.accepts(OpenDialerInput("0300 123-4567")))
        assertEquals("+923001234567", normalizePhoneNumber("+92 300-1234567"))
        listOf("", "tel:+923001234567", "intent://dial", "+92%0A300", "123#45", " 123")
            .forEach { assertFalse(dialer.accepts(OpenDialerInput(it)), it) }

        val email = ToolInputContract(ToolInputKind.EMAIL_COMPOSITION, "email")
        assertTrue(isValidEmailRecipient("test@example.com"))
        assertTrue(
            email.accepts(
                ComposeEmailInput("test@example.com", "KINETIC_EMAIL_OK", "Hello from Kinetic"),
            ),
        )
        listOf(
            ComposeEmailInput(),
            ComposeEmailInput(recipient = "not-an-email"),
            ComposeEmailInput(recipient = "mailto:test@example.com"),
            ComposeEmailInput(recipient = "test@example.com?subject=injected"),
            ComposeEmailInput(recipient = "test@example.com\r\nBcc:x@example.com"),
            ComposeEmailInput(recipient = "test@example.com", subject = "Hello\rBcc: x@example.com"),
            ComposeEmailInput(recipient = "test@example.com", subject = "Hello\nBcc: x@example.com"),
            ComposeEmailInput(subject = "x".repeat(MAX_EMAIL_SUBJECT_LENGTH + 1)),
            ComposeEmailInput(body = "x".repeat(MAX_EMAIL_BODY_LENGTH + 1)),
        ).forEach { assertFalse(email.accepts(it), it.toString()) }
    }

    @Test
    fun `authorization binding changes with every security relevant argument`() {
        assertNotEquals(
            OpenHttpsUrlInput("https://example.com").authorizationBinding(),
            OpenHttpsUrlInput("https://different.example").authorizationBinding(),
        )
        assertNotEquals(
            ShareTextInput("one").authorizationBinding(),
            ShareTextInput("two").authorizationBinding(),
        )
        assertNotEquals(
            OpenSettingsInput(SettingsDestination.GENERAL).authorizationBinding(),
            OpenSettingsInput(SettingsDestination.WIFI).authorizationBinding(),
        )
        assertNotEquals(
            CopyTextToClipboardInput("one").authorizationBinding(),
            CopyTextToClipboardInput("two").authorizationBinding(),
        )
        assertNotEquals(
            OpenDialerInput("+923001234567").authorizationBinding(),
            OpenDialerInput("+923009999999").authorizationBinding(),
        )
        assertNotEquals(
            ComposeEmailInput("a@example.com", "Hello", "Test").authorizationBinding(),
            ComposeEmailInput("b@example.com", "Hello", "Changed").authorizationBinding(),
        )
    }

    @Test
    fun `durable execution rejects changed arguments after approval`() = runTest {
        assertChangedInputRejected(
            "open_https_url",
            OpenHttpsUrlInput("https://example.com"),
            OpenHttpsUrlInput("https://different.example"),
        )
        assertChangedInputRejected(
            "share_text",
            ShareTextInput("one"),
            ShareTextInput("two"),
        )
        assertChangedInputRejected(
            "open_settings",
            OpenSettingsInput(SettingsDestination.GENERAL),
            OpenSettingsInput(SettingsDestination.WIFI),
        )
        assertChangedInputRejected(
            "copy_text_to_clipboard",
            CopyTextToClipboardInput("one"),
            CopyTextToClipboardInput("two"),
        )
        assertChangedInputRejected(
            "open_dialer",
            OpenDialerInput("+923001234567"),
            OpenDialerInput("+923009999999"),
        )
        assertChangedInputRejected(
            "compose_email",
            ComposeEmailInput("a@example.com", "Hello", "Test"),
            ComposeEmailInput("a@example.com", "Hello", "Changed"),
        )
    }

    private suspend fun assertChangedInputRejected(
        toolId: String,
        original: ToolInput,
        changed: ToolInput,
    ) {
        val ledger = InMemoryRunLedger()
        ledger.beginTurn(turn())
        assertTrue(
            ledger.prepareEffect(
                "session",
                "turn",
                "call",
                toolId,
                Instant.EPOCH,
                original.authorizationBinding(),
            ),
        )
        val approval = ApprovalRequest(
            approvalId = "approval",
            sessionId = "session",
            turnId = "turn",
            toolCall = ToolCall("call", toolId, original),
            capability = confirmMetadata(),
            requestedAt = Instant.EPOCH,
        )
        ledger.requestApproval(approval)
        assertTrue(
            ledger.resolveApproval(
                "approval",
                "turn",
                "call",
                ApprovalDecision.APPROVE,
                Instant.EPOCH,
            ),
        )

        assertFalse(
            ledger.markExecuting(
                "turn",
                "call",
                Instant.EPOCH,
                changed.authorizationBinding(),
            ),
        )
        assertTrue(
            ledger.markExecuting(
                "turn",
                "call",
                Instant.EPOCH,
                original.authorizationBinding(),
            ),
        )
    }

    private fun turn() = DurableTurnRecord(
        turnId = "turn",
        sessionId = "session",
        requestId = "request",
        requestSummary = "summary",
        status = DurableTurnStatus.THINKING,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun confirmMetadata() = CapabilityMetadata(
        riskLevel = RiskLevel.CONFIRM,
        requiresConfirmation = true,
        distributionAvailability = setOf(DistributionProfile.PLAY_CORE),
    )
}
