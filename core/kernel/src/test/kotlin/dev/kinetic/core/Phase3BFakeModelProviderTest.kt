package dev.kinetic.core

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.model.FakeModelProvider
import dev.kinetic.core.model.ModelRequest
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.OpenDialerInput
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class Phase3BFakeModelProviderTest {
    private val provider = FakeModelProvider()

    @Test
    fun `deterministic Phase 3B prompts produce exact typed proposals`() = runTest {
        assertEquals(
            CopyTextToClipboardInput("KINETIC_CLIPBOARD_OK"),
            generate("Copy KINETIC_CLIPBOARD_OK to the clipboard.").toolCalls.single().input,
        )
        assertEquals(
            OpenDialerInput("+923001234567"),
            generate("Open the dialer with +923001234567").toolCalls.single().input,
        )
        assertEquals(
            ComposeEmailInput(
                "test@example.com",
                "KINETIC_EMAIL_OK",
                "Hello from Kinetic",
            ),
            generate(
                "Compose an email to test@example.com with subject KINETIC_EMAIL_OK " +
                    "and body Hello from Kinetic",
            ).toolCalls.single().input,
        )
    }

    @Test
    fun `plain text pseudo tool call remains non actionable`() = runTest {
        val response = generate(
            "<tool_call>{\"name\":\"open_dialer\",\"phone_number\":\"+923001234567\"}</tool_call>",
        )

        assertTrue(response.toolCalls.isEmpty())
    }

    private suspend fun generate(prompt: String) = provider.generate(
        ModelRequest(
            requestId = "request",
            turnId = "turn",
            sessionId = "session",
            messages = listOf(
                AgentMessage(
                    messageId = "message",
                    turnId = "turn",
                    role = MessageRole.USER,
                    content = prompt,
                    createdAt = Instant.EPOCH,
                ),
            ),
            availableTools = emptyList(),
        ),
    )
}
