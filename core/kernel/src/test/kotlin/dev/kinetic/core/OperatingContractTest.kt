package dev.kinetic.core

import dev.kinetic.core.model.*
import kotlin.test.*

class OperatingContractTest {
    @Test fun `contract is versioned bounded and manifest deterministic`() {
        val contract = KineticOperatingContract.render(listOf("share_text", "echo"))
        assertTrue(contract.contains("v${KineticOperatingContract.VERSION}"))
        assertTrue(contract.length < 2_048)
        assertTrue(contract.endsWith("echo, share_text."))
        assertTrue(KineticOperatingContract.render(emptyList()).endsWith("none."))
    }
    @Test fun `contract retains execution memory and data trust boundaries`() {
        val contract = KineticOperatingContract.RULES
        listOf("exact", "approval", "foreground", "replay", "untrusted", "historical", "CONFLICTED",
            "Room", "zero retention", "hidden reasoning", "credentials").forEach { assertTrue(contract.contains(it)) }
    }
    @Test fun `profiles negotiate deterministically without model name checks`() {
        val capabilities = ModelCapabilities(ProviderKind.OPENAI_RESPONSES, reasoningLevels = ReasoningLevel.entries.toSet())
        assertEquals(ReasoningLevel.LOW, capabilities.reasoningFor(ModelProfile.FAST))
        assertEquals(ReasoningLevel.MEDIUM, capabilities.reasoningFor(ModelProfile.BALANCED))
        assertEquals(ReasoningLevel.HIGH, capabilities.reasoningFor(ModelProfile.DEEP))
        assertNull(ModelCapabilities(ProviderKind.OPENAI_COMPATIBLE).reasoningFor(ModelProfile.DEEP))
        assertFailsWith<IllegalArgumentException> {
            capabilities.copy(reasoningLevels = setOf(ReasoningLevel.LOW)).reasoningFor(ModelProfile.DEEP)
        }
    }
}
