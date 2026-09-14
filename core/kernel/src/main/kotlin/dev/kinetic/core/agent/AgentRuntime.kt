package dev.kinetic.core.agent

import dev.kinetic.core.logging.EventJournal
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.logging.JournalSanitizer
import dev.kinetic.core.model.ModelProvider
import dev.kinetic.core.model.ModelProviderException
import dev.kinetic.core.model.ModelProviderFailureKind
import dev.kinetic.core.model.ModelContinuationRequest
import dev.kinetic.core.model.ModelRequest
import dev.kinetic.core.model.ModelResponse
import dev.kinetic.core.model.ModelStreamEvent
import dev.kinetic.core.model.ModelToolSupport
import dev.kinetic.core.memory.InMemoryMemoryRepository
import dev.kinetic.core.memory.MemoryRepository
import dev.kinetic.core.context.ContextPlan
import dev.kinetic.core.context.ContextPlanner
import dev.kinetic.core.context.InMemorySessionSummaryRepository
import dev.kinetic.core.context.SessionSummaryRepository
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalGate
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.policy.CapabilityPolicy
import dev.kinetic.core.policy.PolicyContext
import dev.kinetic.core.policy.PolicyDecision
import dev.kinetic.core.session.DurableApprovalStatus
import dev.kinetic.core.session.DurableEffectStatus
import dev.kinetic.core.session.DurableRunSnapshot
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.session.RunLedger
import dev.kinetic.core.session.SessionStore
import dev.kinetic.core.tools.RecoveredToolOutput
import dev.kinetic.core.tools.ToolAvailability
import dev.kinetic.core.tools.ToolAvailabilityStatus
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolExecutionContext
import dev.kinetic.core.tools.ToolRegistry
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference

sealed interface RuntimeRecovery {
    data object None : RuntimeRecovery
    data class Restored(val state: RuntimeState) : RuntimeRecovery
    data class PendingApproval(val snapshot: DurableRunSnapshot) : RuntimeRecovery
}

data class StreamingModelOutput(
    val turnId: String,
    val content: String,
)

interface AgentRuntime {
    val state: StateFlow<RuntimeState>
    val streamingOutput: StateFlow<StreamingModelOutput?>
    val contextPlan: StateFlow<ContextPlan?>

    suspend fun runTurn(request: AgentRequest): AgentTurnResult
    suspend fun recover(sessionId: String): RuntimeRecovery
    suspend fun resumePendingApproval(recovery: RuntimeRecovery.PendingApproval): AgentTurnResult
    fun cancelActiveTurn()
}

/**
 * The provider proposes; typed registry, policy, approval, and durable effect identity authorize.
 * Durable writes precede every externally meaningful state so process death always fails closed.
 */
class DefaultAgentRuntime(
    private val modelProvider: ModelProvider,
    private val toolRegistry: ToolRegistry,
    private val capabilityPolicy: CapabilityPolicy,
    private val policyContext: PolicyContext,
    private val approvalGate: ApprovalGate,
    private val sessionStore: SessionStore,
    private val runLedger: RunLedger,
    private val journal: EventJournal,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val memoryRepository: MemoryRepository = InMemoryMemoryRepository(),
    private val sessionSummaryRepository: SessionSummaryRepository = InMemorySessionSummaryRepository(),
    private val contextPlanner: ContextPlanner = ContextPlanner(),
    private val stateMachine: AgentStateMachine = AgentStateMachine(),
) : AgentRuntime {
    private val turnLock = Mutex()
    private val activeJob = AtomicReference<Job?>(null)
    private val mutableStreamingOutput = MutableStateFlow<StreamingModelOutput?>(null)
    private val mutableContextPlan = MutableStateFlow<ContextPlan?>(null)

    override val state: StateFlow<RuntimeState> = stateMachine.state
    override val streamingOutput: StateFlow<StreamingModelOutput?> =
        mutableStreamingOutput.asStateFlow()
    override val contextPlan: StateFlow<ContextPlan?> = mutableContextPlan.asStateFlow()

    override suspend fun runTurn(request: AgentRequest): AgentTurnResult {
        val turnId = idGenerator.nextId("turn")
        if (!turnLock.tryLock()) return alreadyActive(turnId)

        val runningJob = currentCoroutineContext()[Job]
        activeJob.set(runningJob)
        var durableTurnReady = false

        try {
            prepareForTurn()
            mutableStreamingOutput.value = null
            sessionStore.getOrCreate(request.sessionId, request.createdAt)
            runLedger.beginTurn(
                DurableTurnRecord(
                    turnId = turnId,
                    sessionId = request.sessionId,
                    requestId = request.requestId,
                    requestSummary = JournalSanitizer.redact(request.content),
                    status = DurableTurnStatus.THINKING,
                    createdAt = request.createdAt,
                    updatedAt = request.createdAt,
                ),
            )
            durableTurnReady = true
            sessionStore.appendMessage(
                request.sessionId,
                AgentMessage(
                    messageId = idGenerator.nextId("message"),
                    turnId = turnId,
                    role = MessageRole.USER,
                    content = request.content,
                    createdAt = request.createdAt,
                ),
            )
            journal.record(
                request.sessionId,
                turnId,
                JournalEvent.UserRequestRecorded(
                    request.requestId,
                    JournalSanitizer.redact(request.content),
                ),
            )
            transition(request.sessionId, turnId, RuntimeState.Thinking(turnId))

            val generated = generateModelResponse(request, turnId)
            val modelResponse = generated.response
            if (modelResponse.content.isNotBlank()) appendAssistantMessage(
                request.sessionId,
                turnId,
                modelResponse.content,
            )
            journal.record(
                request.sessionId,
                turnId,
                JournalEvent.ModelResponseRecorded(
                    responseId = modelResponse.responseId,
                    summary = JournalSanitizer.redact(modelResponse.content),
                    toolCallIds = modelResponse.toolCalls.map { it.callId },
                ),
            )

            if (modelResponse.toolCalls.isEmpty()) {
                runLedger.completeTurn(
                    turnId,
                    JournalSanitizer.redact(modelResponse.content),
                    clock.instant(),
                )
                transition(
                    request.sessionId,
                    turnId,
                    RuntimeState.Completed(turnId, modelResponse.content),
                )
                return AgentTurnResult.Completed(turnId, modelResponse)
            }
            if (modelResponse.toolCalls.size != 1) {
                return failTurn(
                    request.sessionId,
                    turnId,
                    InvalidToolCall(
                        toolId = "multiple",
                        userMessage = "Phase 2B accepts at most one structured tool call per response.",
                    ),
                )
            }

            val toolCall = modelResponse.toolCalls.single()
            val exposedIds = generated.request.availableTools.mapTo(mutableSetOf()) { it.id }
            if (toolCall.toolId !in exposedIds ||
                toolCall.proposalTurnId != null && toolCall.proposalTurnId != turnId ||
                toolCall.index != 0
            ) {
                return failTurn(
                    request.sessionId,
                    turnId,
                    InvalidToolCall(
                        toolCall.toolId,
                        "The structured tool proposal was not exposed or did not belong to this turn.",
                    ),
                )
            }
            val tool = toolRegistry.resolve(toolCall.toolId)
                ?: return failTurn(request.sessionId, turnId, InvalidToolCall(toolCall.toolId))
            if (!tool.definition.inputContract.accepts(toolCall.input)) {
                return failTurn(
                    request.sessionId,
                    turnId,
                    InvalidToolCall(toolCall.toolId, "The tool input did not match its typed contract."),
                )
            }

            journal.record(
                request.sessionId,
                turnId,
                JournalEvent.ToolRequested(
                    callId = toolCall.callId,
                    toolId = toolCall.toolId,
                    inputSummary = toolCall.input.journalSummary(),
                ),
            )
            if (!runLedger.prepareEffect(
                sessionId = request.sessionId,
                turnId = turnId,
                callId = toolCall.callId,
                toolId = toolCall.toolId,
                at = clock.instant(),
                inputBinding = toolCall.input.authorizationBinding(),
            )) {
                return failTurn(
                    request.sessionId,
                    turnId,
                    InvalidToolCall(
                        toolCall.toolId,
                        "This tool-call identity was already used and cannot be replayed.",
                    ),
                )
            }

            when (val policyDecision = capabilityPolicy.evaluate(tool.definition, policyContext)) {
                PolicyDecision.Allow -> Unit
                PolicyDecision.RequireApproval -> {
                    val approvalRequest = ApprovalRequest(
                        approvalId = idGenerator.nextId("approval"),
                        sessionId = request.sessionId,
                        turnId = turnId,
                        toolCall = toolCall,
                        capability = tool.definition.capability,
                        requestedAt = clock.instant(),
                    )
                    runLedger.requestApproval(approvalRequest)
                    journal.record(
                        request.sessionId,
                        turnId,
                        JournalEvent.ApprovalRequested(approvalRequest.approvalId, toolCall.callId),
                    )
                    transition(
                        request.sessionId,
                        turnId,
                        RuntimeState.WaitingForApproval(turnId, approvalRequest),
                    )
                    val decision = approvalGate.awaitDecision(approvalRequest)
                    check(persistObservedDecision(approvalRequest, decision)) {
                        "Approval decision did not match the durable pending record"
                    }
                    when (decision) {
                        ApprovalDecision.APPROVE -> journal.record(
                            request.sessionId,
                            turnId,
                            JournalEvent.ApprovalAccepted(
                                approvalRequest.approvalId,
                                toolCall.callId,
                            ),
                        )
                        ApprovalDecision.REJECT ->
                            return rejectTurn(request.sessionId, turnId, approvalRequest)
                    }
                }
                is PolicyDecision.Deny -> return failTurn(
                    request.sessionId,
                    turnId,
                    policyDecision.error,
                )
            }

            return executePreparedTool(
                request.sessionId,
                turnId,
                toolCall,
                modelResponse,
                generated.request,
                continueModel = true,
            )
        } catch (failure: RuntimeAgentFailure) {
            return failTurn(request.sessionId, turnId, failure.error)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (durableTurnReady) cancelTurn(request.sessionId, turnId, Cancelled)
                else moveToCancelledWithoutPersistence(turnId)
            }
            throw cancelled
        } catch (_: Throwable) {
            val error = InternalFailure(idGenerator.nextId("incident"))
            return if (durableTurnReady) {
                failTurn(request.sessionId, turnId, error)
            } else {
                if (state.value.phase == RuntimePhase.IDLE) {
                    stateMachine.transition(RuntimeState.Thinking(turnId))
                }
                stateMachine.transition(RuntimeState.Failed(turnId, error))
                AgentTurnResult.Failed(turnId, error)
            }
        } finally {
            modelProvider.finishTurn(turnId)
            activeJob.compareAndSet(runningJob, null)
            turnLock.unlock()
        }
    }

    override suspend fun recover(sessionId: String): RuntimeRecovery {
        check(state.value == RuntimeState.Idle) { "Recovery must initialize an idle runtime" }
        val snapshot = runLedger.latestRun(sessionId) ?: return RuntimeRecovery.None
        return when (snapshot.turn.status) {
            DurableTurnStatus.THINKING -> interruptRecovery(snapshot, RuntimePhase.THINKING)
            DurableTurnStatus.EXECUTING -> interruptRecovery(snapshot, RuntimePhase.EXECUTING)
            DurableTurnStatus.WAITING_FOR_APPROVAL -> recoverWaiting(snapshot)
            DurableTurnStatus.COMPLETED -> restoreCompleted(snapshot)
            DurableTurnStatus.FAILED -> restoreFailed(snapshot)
            DurableTurnStatus.CANCELLED -> restoreCancelled(snapshot)
        }
    }

    override suspend fun resumePendingApproval(
        recovery: RuntimeRecovery.PendingApproval,
    ): AgentTurnResult {
        val snapshot = recovery.snapshot
        val request = requireNotNull(snapshot.approval).request
        check(state.value is RuntimeState.WaitingForApproval) { "No recovered approval is waiting" }
        if (!turnLock.tryLock()) return alreadyActive(snapshot.turn.turnId)

        val runningJob = currentCoroutineContext()[Job]
        activeJob.set(runningJob)
        try {
            val decision = approvalGate.awaitDecision(request)
            check(persistObservedDecision(request, decision)) {
                "Recovered approval decision did not match the durable pending record"
            }
            return when (decision) {
                ApprovalDecision.REJECT -> rejectTurn(request.sessionId, request.turnId, request)
                ApprovalDecision.APPROVE -> {
                    journal.record(
                        request.sessionId,
                        request.turnId,
                        JournalEvent.ApprovalAccepted(
                            request.approvalId,
                            request.toolCall.callId,
                        ),
                    )
                    val assistant = sessionStore.get(request.sessionId)?.messages
                        ?.lastOrNull {
                            it.turnId == request.turnId && it.role == MessageRole.ASSISTANT
                        }
                        ?.content
                        .orEmpty()
                    executePreparedTool(
                        sessionId = request.sessionId,
                        turnId = request.turnId,
                        toolCall = request.toolCall,
                        modelResponse = ModelResponse(
                            responseId = "recovered-${request.turnId}",
                            content = assistant,
                            toolCalls = listOf(request.toolCall),
                        ),
                        originalRequest = null,
                        continueModel = false,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                cancelTurn(request.sessionId, request.turnId, Cancelled)
            }
            throw cancelled
        } catch (_: Throwable) {
            return failTurn(
                request.sessionId,
                request.turnId,
                InternalFailure(idGenerator.nextId("incident")),
            )
        } finally {
            activeJob.compareAndSet(runningJob, null)
            turnLock.unlock()
        }
    }

    override fun cancelActiveTurn() {
        activeJob.get()?.cancel(CancellationException("Cancelled by user"))
    }

    private suspend fun executePreparedTool(
        sessionId: String,
        turnId: String,
        toolCall: ToolCall,
        modelResponse: ModelResponse,
        originalRequest: ModelRequest?,
        continueModel: Boolean,
    ): AgentTurnResult {
        val tool = toolRegistry.resolve(toolCall.toolId)
            ?: return failTurn(sessionId, turnId, InvalidToolCall(toolCall.toolId))
        if (!tool.definition.inputContract.accepts(toolCall.input)) {
            return failTurn(sessionId, turnId, InvalidToolCall(toolCall.toolId))
        }
        val executionContext = ToolExecutionContext(sessionId, turnId, toolCall.callId)
        val availability = try {
            tool.checkAvailability(toolCall.input, executionContext)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ToolAvailability.Unavailable(
                ToolAvailabilityStatus.UNAVAILABLE_PRECONDITION,
                "Kinetic could not verify that this action is currently available.",
            )
        }
        journal.record(
            sessionId,
            turnId,
            JournalEvent.ToolAvailabilityChecked(
                toolCall.callId,
                toolCall.toolId,
                availability.status,
            ),
        )
        if (availability is ToolAvailability.Unavailable) {
            journal.record(
                sessionId,
                turnId,
                JournalEvent.ToolDispatchFailed(
                    toolCall.callId,
                    toolCall.toolId,
                    availability.status.name,
                ),
            )
            return failTurn(
                sessionId,
                turnId,
                ToolFailure(toolCall.toolId, availability.userMessage),
            )
        }
        if (!runLedger.markExecuting(
                turnId = turnId,
                callId = toolCall.callId,
                at = clock.instant(),
                inputBinding = toolCall.input.authorizationBinding(),
            )
        ) {
            return failTurn(
                sessionId,
                turnId,
                InternalFailure(
                    idGenerator.nextId("incident"),
                    "The persisted authorization or effect identity was no longer valid.",
                ),
            )
        }
        transition(sessionId, turnId, RuntimeState.Executing(turnId, toolCall))
        journal.record(
            sessionId,
            turnId,
            JournalEvent.ToolExecutionStarted(toolCall.callId, toolCall.toolId),
        )

        val toolResult = try {
            tool.execute(
                input = toolCall.input,
                context = executionContext,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ToolResult.Failure(toolCall.callId, toolCall.toolId, ToolFailure(toolCall.toolId))
        }

        return when (toolResult) {
            is ToolResult.Success -> {
                val output = toolResult.output.displayText()
                runLedger.completeEffect(
                    turnId,
                    toolCall.callId,
                    JournalSanitizer.redact(output),
                    clock.instant(),
                )
                journal.record(
                    sessionId,
                    turnId,
                    JournalEvent.ToolExecutionCompleted(
                        toolCall.callId,
                        toolCall.toolId,
                        JournalSanitizer.redact(output),
                    ),
                )
                sessionStore.appendMessage(
                    sessionId,
                    AgentMessage(
                        messageId = idGenerator.nextId("message"),
                        turnId = turnId,
                        role = MessageRole.TOOL,
                        content = output,
                        createdAt = clock.instant(),
                    ),
                )
                val finalResponse = if (continueModel && originalRequest != null) {
                    generateModelContinuation(
                        ModelContinuationRequest(originalRequest, modelResponse, toolResult),
                        turnId,
                    ).also { continuation ->
                        appendAssistantMessage(sessionId, turnId, continuation.content)
                        journal.record(
                            sessionId,
                            turnId,
                            JournalEvent.ModelResponseRecorded(
                                responseId = continuation.responseId,
                                summary = JournalSanitizer.redact(continuation.content),
                                toolCallIds = emptyList(),
                            ),
                        )
                    }
                } else {
                    ModelResponse(
                        responseId = "tool-complete-$turnId",
                        content = output,
                    )
                }
                runLedger.completeTurn(
                    turnId,
                    JournalSanitizer.redact(finalResponse.content),
                    clock.instant(),
                )
                transition(
                    sessionId,
                    turnId,
                    RuntimeState.Completed(turnId, finalResponse.content, toolResult),
                )
                AgentTurnResult.Completed(turnId, finalResponse, toolResult)
            }
            is ToolResult.Failure -> {
                journal.record(
                    sessionId,
                    turnId,
                    JournalEvent.ToolDispatchFailed(
                        toolCall.callId,
                        toolCall.toolId,
                        toolResult.error.code,
                    ),
                )
                failTurn(sessionId, turnId, toolResult.error)
            }
        }
    }

    private suspend fun rejectTurn(
        sessionId: String,
        turnId: String,
        approvalRequest: ApprovalRequest,
    ): AgentTurnResult.Cancelled {
        val error = ApprovalRejected(approvalRequest.approvalId)
        runLedger.cancelTurn(turnId, error, clock.instant())
        journal.record(
            sessionId,
            turnId,
            JournalEvent.ApprovalRejected(
                approvalRequest.approvalId,
                approvalRequest.toolCall.callId,
            ),
        )
        transition(sessionId, turnId, RuntimeState.Cancelled(turnId, error))
        return AgentTurnResult.Cancelled(turnId, error)
    }

    private suspend fun persistObservedDecision(
        request: ApprovalRequest,
        decision: ApprovalDecision,
    ): Boolean = runLedger.resolveApproval(
        approvalId = request.approvalId,
        turnId = request.turnId,
        callId = request.toolCall.callId,
        decision = decision,
        at = clock.instant(),
    )

    private suspend fun cancelTurn(sessionId: String, turnId: String, error: AgentError) {
        mutableStreamingOutput.value = null
        approvalGate.cancelPending(turnId)
        runLedger.cancelTurn(turnId, error, clock.instant())
        journal.record(sessionId, turnId, JournalEvent.CancellationRecorded(error.code))
        if (state.value.phase !in terminalPhases) {
            transition(sessionId, turnId, RuntimeState.Cancelled(turnId, error))
        }
    }

    private fun moveToCancelledWithoutPersistence(turnId: String) {
        if (state.value.phase == RuntimePhase.IDLE) {
            stateMachine.transition(RuntimeState.Thinking(turnId))
        }
        if (state.value.phase !in terminalPhases) {
            stateMachine.transition(RuntimeState.Cancelled(turnId, Cancelled))
        }
    }

    private fun prepareForTurn() {
        when (state.value.phase) {
            RuntimePhase.COMPLETED,
            RuntimePhase.FAILED,
            RuntimePhase.CANCELLED,
            -> stateMachine.transition(RuntimeState.Idle)
            RuntimePhase.IDLE -> Unit
            else -> throw IllegalStateException("A turn is already active")
        }
    }

    private data class GeneratedModelResponse(
        val request: ModelRequest,
        val response: ModelResponse,
    )

    private suspend fun generateModelResponse(
        request: AgentRequest,
        turnId: String,
    ): GeneratedModelResponse = try {
        val session = requireNotNull(sessionStore.get(request.sessionId))
        modelProvider.prepareTurn(turnId, dev.kinetic.core.model.RoutingRequirements(
            minimumContextTokens = dev.kinetic.core.context.estimateTokens(request.content) + 320,
        ))
        val support = modelProvider.toolSupport()
        val exposedIds = (support as? ModelToolSupport.Structured)?.exposedToolIds.orEmpty()
        val memoryCandidates = memoryRepository.candidates(request.sessionId)
        val summary = sessionSummaryRepository.latest(request.sessionId)
        val plan = contextPlanner.plan(
            sessionId = request.sessionId,
            query = request.content,
            candidates = memoryCandidates,
            summary = summary,
            messages = session.messages,
            providerMaxContextTokens = modelProvider.capabilities().maxContextTokens,
            providerReservedOutputTokens = modelProvider.capabilities().reservedOutputTokens,
            providerReservedSystemTokens = modelProvider.capabilities().reservedSystemTokens,
        )
        mutableContextPlan.value = plan
        val memoryContext = plan.memoryContext
        if (memoryContext.ordered.isNotEmpty()) {
            journal.record(
                request.sessionId,
                turnId,
                JournalEvent.MemoryInjected(
                    memoryIds = memoryContext.ordered.map { it.memoryId },
                    userCount = memoryContext.userMemories.size,
                    sessionCount = memoryContext.sessionMemories.size,
                ),
            )
        }
        val modelRequest = ModelRequest(
            requestId = request.requestId,
            turnId = turnId,
            sessionId = request.sessionId,
            messages = session.messages,
            availableTools = toolRegistry.definitions().filter { it.id in exposedIds },
            memoryContext = memoryContext,
            sessionSummary = plan.summary,
            contextPlan = plan,
        )
        var completed: ModelResponse? = null
        modelProvider.stream(modelRequest).collect { event ->
            check(completed == null) { "Model stream emitted data after completion" }
            when (event) {
                is ModelStreamEvent.TextDelta -> if (event.text.isNotEmpty()) {
                    val current = mutableStreamingOutput.value
                        ?.takeIf { it.turnId == turnId }
                        ?.content
                        .orEmpty()
                    mutableStreamingOutput.value = StreamingModelOutput(turnId, current + event.text)
                }
                is ModelStreamEvent.Completed -> completed = event.response
                ModelStreamEvent.Started,
                is ModelStreamEvent.ToolCallStarted,
                is ModelStreamEvent.ToolArgumentDelta,
                is ModelStreamEvent.ToolCallCompleted -> Unit // Progress is never authorization.
            }
        }
        val response = completed ?: throw ModelProviderException(
            kind = ModelProviderFailureKind.MALFORMED_RESPONSE,
            safeMessage = "The model stream closed before completion.",
        )
        if (support is ModelToolSupport.Unavailable && response.toolCalls.isNotEmpty()) {
            throw ModelProviderException(
                kind = ModelProviderFailureKind.MALFORMED_RESPONSE,
                safeMessage = "The conversation provider returned an unsupported tool request.",
            )
        }
        mutableStreamingOutput.value = StreamingModelOutput(turnId, response.content)
        GeneratedModelResponse(modelRequest, response)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ModelProviderException) {
        val userMessage = if (failure.kind == ModelProviderFailureKind.UNEXPECTED) {
            "The model could not complete the request."
        } else {
            failure.safeMessage
        }
        throw RuntimeAgentFailure(
            ModelFailure(modelProvider.providerId, failure.kind, userMessage),
        )
    } catch (_: Throwable) {
        throw RuntimeAgentFailure(ModelFailure(modelProvider.providerId))
    }

    private suspend fun generateModelContinuation(
        request: ModelContinuationRequest,
        turnId: String,
    ): ModelResponse = try {
        mutableStreamingOutput.value = StreamingModelOutput(turnId, "")
        var completed: ModelResponse? = null
        modelProvider.streamContinuation(request).collect { event ->
            check(completed == null) { "Model continuation emitted data after completion" }
            when (event) {
                is ModelStreamEvent.TextDelta -> if (event.text.isNotEmpty()) {
                    val current = mutableStreamingOutput.value
                        ?.takeIf { it.turnId == turnId }
                        ?.content
                        .orEmpty()
                    mutableStreamingOutput.value = StreamingModelOutput(turnId, current + event.text)
                }
                is ModelStreamEvent.Completed -> completed = event.response
                ModelStreamEvent.Started,
                is ModelStreamEvent.ToolCallStarted,
                is ModelStreamEvent.ToolArgumentDelta,
                is ModelStreamEvent.ToolCallCompleted -> Unit
            }
        }
        val response = completed ?: throw ModelProviderException(
            ModelProviderFailureKind.MALFORMED_RESPONSE,
            "The model continuation closed before completion.",
        )
        if (response.toolCalls.isNotEmpty()) throw ModelProviderException(
            ModelProviderFailureKind.MALFORMED_RESPONSE,
            "A second tool call is not supported in the Phase 2B continuation.",
        )
        mutableStreamingOutput.value = StreamingModelOutput(turnId, response.content)
        response
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ModelProviderException) {
        throw RuntimeAgentFailure(
            ModelFailure(modelProvider.providerId, failure.kind, failure.safeMessage),
        )
    } catch (_: Throwable) {
        throw RuntimeAgentFailure(ModelFailure(modelProvider.providerId))
    }

    private suspend fun appendAssistantMessage(sessionId: String, turnId: String, content: String) {
        if (content.isBlank()) return
        sessionStore.appendMessage(
            sessionId,
            AgentMessage(
                messageId = idGenerator.nextId("message"),
                turnId = turnId,
                role = MessageRole.ASSISTANT,
                content = content,
                createdAt = clock.instant(),
            ),
        )
    }

    private suspend fun transition(
        sessionId: String,
        turnId: String,
        next: RuntimeState,
    ) {
        val previous = stateMachine.transition(next)
        journal.record(
            sessionId,
            turnId,
            JournalEvent.StateTransition(previous.phase, next.phase),
        )
    }

    private suspend fun failTurn(
        sessionId: String,
        turnId: String,
        error: AgentError,
    ): AgentTurnResult.Failed {
        mutableStreamingOutput.value = null
        runLedger.failTurn(turnId, error, clock.instant())
        journal.record(sessionId, turnId, JournalSanitizer.error(error))
        if (state.value.phase !in terminalPhases) {
            transition(sessionId, turnId, RuntimeState.Failed(turnId, error))
        }
        return AgentTurnResult.Failed(turnId, error)
    }

    private suspend fun recoverWaiting(snapshot: DurableRunSnapshot): RuntimeRecovery {
        val approval = snapshot.approval
        return when (approval?.status) {
            DurableApprovalStatus.PENDING -> {
                val waiting = RuntimeState.WaitingForApproval(
                    snapshot.turn.turnId,
                    approval.request,
                )
                stateMachine.restore(waiting)
                RuntimeRecovery.PendingApproval(snapshot)
            }
            DurableApprovalStatus.REJECTED -> {
                val error = ApprovalRejected(approval.request.approvalId)
                runLedger.cancelTurn(snapshot.turn.turnId, error, clock.instant())
                journal.record(
                    snapshot.turn.sessionId,
                    snapshot.turn.turnId,
                    JournalEvent.ApprovalRejected(
                        approval.request.approvalId,
                        approval.request.toolCall.callId,
                    ),
                )
                journal.record(
                    snapshot.turn.sessionId,
                    snapshot.turn.turnId,
                    JournalEvent.StateTransition(
                        RuntimePhase.WAITING_FOR_APPROVAL,
                        RuntimePhase.CANCELLED,
                    ),
                )
                val cancelled = RuntimeState.Cancelled(snapshot.turn.turnId, error)
                stateMachine.restore(cancelled)
                RuntimeRecovery.Restored(cancelled)
            }
            else -> interruptRecovery(snapshot, RuntimePhase.WAITING_FOR_APPROVAL)
        }
    }

    private suspend fun interruptRecovery(
        snapshot: DurableRunSnapshot,
        phase: RuntimePhase,
    ): RuntimeRecovery.Restored {
        val error = RecoveryInterrupted(phase)
        runLedger.failTurn(snapshot.turn.turnId, error, clock.instant())
        journal.record(snapshot.turn.sessionId, snapshot.turn.turnId, JournalSanitizer.error(error))
        journal.record(
            snapshot.turn.sessionId,
            snapshot.turn.turnId,
            JournalEvent.StateTransition(phase, RuntimePhase.FAILED),
        )
        val failed = RuntimeState.Failed(snapshot.turn.turnId, error)
        stateMachine.restore(failed)
        return RuntimeRecovery.Restored(failed)
    }

    private fun restoreCompleted(snapshot: DurableRunSnapshot): RuntimeRecovery.Restored {
        val effect = snapshot.effect?.takeIf { it.status == DurableEffectStatus.COMPLETED }
        val toolResult = effect?.let {
            ToolResult.Success(
                callId = it.callId,
                toolId = it.toolId,
                output = RecoveredToolOutput(it.outputSummary.orEmpty()),
            )
        }
        val completed = RuntimeState.Completed(
            turnId = snapshot.turn.turnId,
            summary = snapshot.turn.completionSummary.orEmpty(),
            toolResult = toolResult,
        )
        stateMachine.restore(completed)
        return RuntimeRecovery.Restored(completed)
    }

    private fun restoreFailed(snapshot: DurableRunSnapshot): RuntimeRecovery.Restored {
        val failed = RuntimeState.Failed(snapshot.turn.turnId, snapshot.restoredError())
        stateMachine.restore(failed)
        return RuntimeRecovery.Restored(failed)
    }

    private fun restoreCancelled(snapshot: DurableRunSnapshot): RuntimeRecovery.Restored {
        val error = if (snapshot.turn.errorCode == "approval_rejected" && snapshot.approval != null) {
            ApprovalRejected(snapshot.approval.request.approvalId)
        } else {
            snapshot.restoredError()
        }
        val cancelled = RuntimeState.Cancelled(snapshot.turn.turnId, error)
        stateMachine.restore(cancelled)
        return RuntimeRecovery.Restored(cancelled)
    }

    private fun DurableRunSnapshot.restoredError(): AgentError = RestoredAgentError(
        code = turn.errorCode ?: "restored_failure",
        userMessage = turn.errorMessage ?: "The restored turn ended without a safe detail.",
    )

    private fun alreadyActive(turnId: String) = AgentTurnResult.Failed(
        turnId,
        InternalFailure(
            incidentId = idGenerator.nextId("incident"),
            userMessage = "Another agent turn is already active.",
        ),
    )

    private class RuntimeAgentFailure(val error: AgentError) : RuntimeException()

    private companion object {
        val terminalPhases = setOf(
            RuntimePhase.COMPLETED,
            RuntimePhase.FAILED,
            RuntimePhase.CANCELLED,
        )
    }
}
