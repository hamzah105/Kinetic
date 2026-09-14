package dev.kinetic.core.context

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.memory.MAX_SESSION_MEMORIES_IN_CONTEXT
import dev.kinetic.core.memory.MAX_USER_MEMORIES_IN_CONTEXT
import dev.kinetic.core.memory.MemoryCategory
import dev.kinetic.core.memory.MemoryContext
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryRetentionState
import dev.kinetic.core.memory.MemoryScope
import java.util.Locale
import kotlin.math.ceil

data class ContextBudget(
    val totalEstimatedTokens: Int = 8_192,
    val reservedOutputTokens: Int = 1_024,
    val reservedSystemTokens: Int = 512,
    val maxRecentMessages: Int = 20,
) {
    init {
        require(totalEstimatedTokens > reservedOutputTokens + reservedSystemTokens)
        require(reservedOutputTokens > 0 && reservedSystemTokens > 0)
        require(maxRecentMessages in 1..100)
    }
}

data class MemoryRetrievalDiagnostic(
    val memoryId: String,
    val scope: MemoryScope,
    val category: MemoryCategory,
    val score: Int,
    val matchedTermCount: Int,
    val exactNormalizedMatch: Boolean,
    val retentionState: MemoryRetentionState,
    val governanceKeyHashPrefix: String?,
)

data class ContextPlan(
    val sessionId: String,
    val memoryContext: MemoryContext,
    val memoryDiagnostics: List<MemoryRetrievalDiagnostic>,
    val memoriesConsidered: Int,
    val summary: SessionSummary?,
    val selectedMessages: List<AgentMessage>,
    val selectedMessageFirstSequence: Long?,
    val selectedMessageLastSequence: Long?,
    val omittedCoveredMessages: Int,
    val omittedBudgetMessages: Int,
    val estimatedInputTokens: Int,
    val reservedOutputTokens: Int,
    val totalEstimatedTokens: Int,
    val budgetLimitTokens: Int,
    val omissionReasons: List<String>,
)

class DeterministicMemoryRanker {
    fun rank(query: String, candidates: List<MemoryRecord>, limit: Int): List<RankedMemory> {
        require(limit >= 0)
        val queryTerms = meaningfulTerms(query)
        val normalizedQuery = normalize(query)
        if (queryTerms.isEmpty() || limit == 0) return emptyList()
        return candidates.asSequence()
            .filter { it.userVisible && it.retentionState != MemoryRetentionState.SUPERSEDED }
            .map { memory ->
                val memoryTerms = meaningfulTerms(memory.content)
                val overlap = queryTerms.intersect(memoryTerms)
                val exact = normalizedQuery == normalize(memory.content)
                val phraseRun = longestOrderedRun(queryTerms.toList(), memoryTerms.toList())
                val fullCoverage = queryTerms.all { it in memoryTerms }
                val categoryBonus = when {
                    memory.category == MemoryCategory.PREFERENCE &&
                        queryTerms.any { it in PREFERENCE_TERMS } -> 8
                    memory.category == MemoryCategory.TASK_CONTEXT &&
                        queryTerms.any { it in SESSION_TERMS } -> 6
                    else -> 0
                }
                val score = if (overlap.isEmpty()) 0 else {
                    overlap.size * 10 + phraseRun * 4 + categoryBonus +
                        (if (fullCoverage) 30 else 0) + (if (exact) 100 else 0)
                }
                RankedMemory(memory, score, overlap.size, exact)
            }
            .filter { it.score > 0 }
            .sortedWith(
                compareByDescending<RankedMemory> { it.score }
                    .thenByDescending { it.record.updatedAt }
                    .thenBy { it.record.memoryId },
            )
            .take(limit)
            .toList()
    }

    private fun longestOrderedRun(left: List<String>, right: List<String>): Int {
        var best = 0
        for (i in left.indices) for (j in right.indices) {
            var count = 0
            while (i + count < left.size && j + count < right.size &&
                left[i + count] == right[j + count]
            ) count += 1
            best = maxOf(best, count)
        }
        return best
    }

    private fun meaningfulTerms(value: String): LinkedHashSet<String> = normalize(value)
        .split(' ')
        .asSequence()
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .toCollection(linkedSetOf())

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    companion object {
        private val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "for", "from", "has", "have", "how",
            "i", "in", "is", "it", "me", "my", "of", "on", "or", "that", "the", "this", "to",
            "was", "what", "when", "where", "which", "who", "with", "you", "your",
        )
        private val PREFERENCE_TERMS = setOf("prefer", "preferred", "preference", "favorite", "favourite")
        private val SESSION_TERMS = setOf("session", "conversation", "task", "codename", "current")
    }
}

data class RankedMemory(
    val record: MemoryRecord,
    val score: Int,
    val matchedTermCount: Int,
    val exactNormalizedMatch: Boolean,
)

class ContextPlanner(
    private val budget: ContextBudget = ContextBudget(),
    private val ranker: DeterministicMemoryRanker = DeterministicMemoryRanker(),
) {
    fun plan(
        sessionId: String,
        query: String,
        candidates: MemoryContext,
        summary: SessionSummary?,
        messages: List<AgentMessage>,
        providerMaxContextTokens: Int? = null,
        providerReservedOutputTokens: Int? = null,
        providerReservedSystemTokens: Int? = null,
    ): ContextPlan {
        require(sessionId.isNotBlank())
        val selectedBudget = this.budget.copy(
            reservedOutputTokens = providerReservedOutputTokens ?: this.budget.reservedOutputTokens,
            reservedSystemTokens = providerReservedSystemTokens ?: this.budget.reservedSystemTokens,
        )
        val budget = if (providerMaxContextTokens != null && providerMaxContextTokens < selectedBudget.totalEstimatedTokens) {
            if (providerMaxContextTokens <= selectedBudget.reservedOutputTokens + selectedBudget.reservedSystemTokens) {
                throw dev.kinetic.core.model.ModelProviderException(
                    dev.kinetic.core.model.ModelProviderFailureKind.CONTEXT_LIMIT,
                    "The provider context cannot fit Kinetic's system and output reserves.")
            }
            selectedBudget.copy(totalEstimatedTokens = providerMaxContextTokens)
        } else selectedBudget
        val userRanked = ranker.rank(query, candidates.userMemories, MAX_USER_MEMORIES_IN_CONTEXT)
        val sessionRanked = ranker.rank(query, candidates.sessionMemories, MAX_SESSION_MEMORIES_IN_CONTEXT)
        val ranked = userRanked + sessionRanked
        var remaining = budget.totalEstimatedTokens - budget.reservedOutputTokens -
            budget.reservedSystemTokens
        val omissions = mutableListOf<String>()

        val conversation = messages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .sortedBy(AgentMessage::sequence)
        val current = conversation.lastOrNull { it.role == MessageRole.USER }
        val selectedMessages = mutableListOf<AgentMessage>()
        current?.let {
            if (estimateTokens(it.content) > remaining && providerMaxContextTokens != null) {
                throw dev.kinetic.core.model.ModelProviderException(
                    dev.kinetic.core.model.ModelProviderFailureKind.CONTEXT_LIMIT,
                    "The current request exceeds the provider context budget. Shorten the request.")
            }
            selectedMessages += it
            remaining -= estimateTokens(it.content)
        }

        val selectedMemories = mutableListOf<RankedMemory>()
        ranked.forEach { candidate ->
            val cost = estimateTokens(candidate.record.content) + 16
            if (cost <= remaining) {
                selectedMemories += candidate
                remaining -= cost
            } else {
                omissions += "memory_budget"
            }
        }

        val coveredLast = summary?.lastCoveredMessageSequence ?: 0L
        val coveredCount = conversation.count { it.sequence <= coveredLast }
        val uncovered = conversation.filter { it.sequence > coveredLast && it.messageId != current?.messageId }
        val recentSlots = (budget.maxRecentMessages - selectedMessages.size).coerceAtLeast(0)
        var budgetOmitted = 0
        uncovered.asReversed().take(recentSlots).forEach { message ->
            val cost = estimateTokens(message.content) + 8
            if (cost <= remaining) {
                selectedMessages += message
                remaining -= cost
            } else {
                budgetOmitted += 1
            }
        }
        if (uncovered.size > recentSlots) {
            budgetOmitted += uncovered.size - recentSlots
        }
        selectedMessages.sortBy(AgentMessage::sequence)

        val selectedSummary = summary?.takeIf {
            val cost = estimateTokens(it.content) + 24
            if (cost <= remaining) {
                remaining -= cost
                true
            } else {
                omissions += "summary_budget"
                false
            }
        }
        if (budgetOmitted > 0) omissions += "message_budget"
        if (ranked.isEmpty() && candidates.ordered.isNotEmpty()) omissions += "zero_relevance"

        val memoryContext = MemoryContext(
            userMemories = selectedMemories.filter { it.record.scope == MemoryScope.USER }.map { it.record },
            sessionMemories = selectedMemories.filter { it.record.scope == MemoryScope.SESSION }.map { it.record },
        )
        val usedInput = budget.totalEstimatedTokens - budget.reservedOutputTokens - remaining
        return ContextPlan(
            sessionId = sessionId,
            memoryContext = memoryContext,
            memoryDiagnostics = selectedMemories.map {
                MemoryRetrievalDiagnostic(
                    memoryId = it.record.memoryId,
                    scope = it.record.scope,
                    category = it.record.category,
                    score = it.score,
                    matchedTermCount = it.matchedTermCount,
                    exactNormalizedMatch = it.exactNormalizedMatch,
                    retentionState = it.record.retentionState,
                    governanceKeyHashPrefix = it.record.governanceKey?.take(12),
                )
            },
            memoriesConsidered = candidates.ordered.size,
            summary = selectedSummary,
            selectedMessages = selectedMessages,
            selectedMessageFirstSequence = selectedMessages.minOfOrNull(AgentMessage::sequence),
            selectedMessageLastSequence = selectedMessages.maxOfOrNull(AgentMessage::sequence),
            omittedCoveredMessages = coveredCount,
            omittedBudgetMessages = budgetOmitted,
            estimatedInputTokens = usedInput,
            reservedOutputTokens = budget.reservedOutputTokens,
            totalEstimatedTokens = usedInput + budget.reservedOutputTokens,
            budgetLimitTokens = budget.totalEstimatedTokens,
            omissionReasons = omissions.distinct(),
        )
    }
}

fun estimateTokens(value: String): Int = ceil(value.length / 4.0).toInt().coerceAtLeast(1)
