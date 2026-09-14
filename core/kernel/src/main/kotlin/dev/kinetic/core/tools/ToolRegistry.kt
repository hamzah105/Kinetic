package dev.kinetic.core.tools

/** Immutable allowlist. A tool absent from this registry can never be dispatched. */
class ToolRegistry(tools: Iterable<Tool>) {
    private val toolsById: Map<String, Tool>

    init {
        val materialized = tools.toList()
        val duplicates = materialized.groupBy { it.definition.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate tool ids: ${duplicates.sorted()}" }
        toolsById = materialized.associateBy { it.definition.id }
    }

    fun resolve(toolId: String): Tool? = toolsById[toolId]

    fun definitions(): List<ToolDefinition> = toolsById.values
        .map { it.definition }
        .sortedBy { it.id }
}

