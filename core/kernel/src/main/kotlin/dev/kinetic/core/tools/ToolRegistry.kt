package dev.kinetic.core.tools

/** Built-in allowlist plus explicitly reviewed MCP bindings. Absent tools cannot be dispatched. */
class ToolRegistry(tools: Iterable<Tool>) {
    private val toolsById: Map<String, Tool>
    @Volatile private var mcpTools: Map<String, Tool> = emptyMap()
    @Volatile private var appFunctionTools: Map<String, Tool> = emptyMap()

    fun replaceAppFunctions(tools: List<Tool>) {
        require(tools.size <= 64 && tools.all { it.definition.id.matches(Regex("appfn_[a-f0-9]{58}")) &&
            it.definition.capability.riskLevel == dev.kinetic.core.policy.RiskLevel.CONFIRM && it.definition.capability.requiresConfirmation })
        require(tools.map { it.definition.id }.distinct().size == tools.size)
        require(tools.none { it.definition.id in toolsById || it.definition.id in mcpTools })
        appFunctionTools = tools.associateBy { it.definition.id }
    }

    /** Called only by explicit settings management while the app is idle, never by model output. */
    fun replaceMcp(tools: List<Tool>) {
        require(tools.size <= 128 && tools.all { it.definition.id.startsWith("mcp_") &&
            it.definition.capability.riskLevel == dev.kinetic.core.policy.RiskLevel.CONFIRM &&
            it.definition.capability.requiresConfirmation })
        require(tools.map { it.definition.id }.distinct().size == tools.size)
        require(tools.none { it.definition.id in toolsById })
        mcpTools = tools.associateBy { it.definition.id }
    }

    init {
        val materialized = tools.toList()
        val duplicates = materialized.groupBy { it.definition.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate tool ids: ${duplicates.sorted()}" }
        toolsById = materialized.associateBy { it.definition.id }
    }

    fun resolve(toolId: String): Tool? = toolsById[toolId] ?: mcpTools[toolId] ?: appFunctionTools[toolId]

    fun definitions(): List<ToolDefinition> = (toolsById.values + mcpTools.values + appFunctionTools.values)
        .map { it.definition }
        .sortedBy { it.id }
}
