package dev.kinetic.core

import dev.kinetic.core.policy.RiskLevel
import dev.kinetic.core.tools.*
import kotlin.test.*

class AppFunctionBoundaryTest {
    private val id = "appfn_" + "a".repeat(58)
    private fun tool(confirm: Boolean = true): Tool = object : Tool {
        override val definition = ProtectedDemoTool().definition.let {
            it.copy(id = id, capability = it.capability.copy(
                riskLevel = if (confirm) RiskLevel.CONFIRM else RiskLevel.SAFE,
                requiresConfirmation = confirm,
            ))
        }
        override suspend fun execute(input: ToolInput, context: ToolExecutionContext): ToolResult =
            error("Registry tests must never execute an external effect")
    }

    @Test fun `unregistered and removed functions cannot resolve`() {
        val registry = ToolRegistry(emptyList())
        assertNull(registry.resolve(id))
        val reviewed = tool()
        registry.replaceAppFunctions(listOf(reviewed))
        assertSame(reviewed, registry.resolve(id))
        registry.replaceAppFunctions(emptyList())
        assertNull(registry.resolve(id))
    }

    @Test fun `external functions cannot opt out of confirmation`() {
        val registry = ToolRegistry(emptyList())
        assertFailsWith<IllegalArgumentException> { registry.replaceAppFunctions(listOf(tool(false))) }
        assertNull(registry.resolve(id))
    }

    @Test fun `duplicate capability ids are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(emptyList()).replaceAppFunctions(listOf(tool(), tool()))
        }
    }

    @Test fun `approval binding separates protocols and arguments without journaling values`() {
        val input = AppFunctionToolInput("{\"text\":\"private value\"}")
        assertNotEquals(input.authorizationBinding(), McpToolInput(input.canonicalJson).authorizationBinding())
        assertNotEquals(input.authorizationBinding(), AppFunctionToolInput("{}").authorizationBinding())
        assertFalse(input.journalSummary().contains("private value"))
        assertTrue(input.approvalSummary().contains("private value"))
    }

    @Test fun `oversized arguments fail closed`() {
        assertFailsWith<IllegalArgumentException> { AppFunctionToolInput("x".repeat(8193)) }
    }
}
