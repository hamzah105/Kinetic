package dev.kinetic.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.AgentRequest
import dev.kinetic.core.agent.DefaultAgentRuntime
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.agent.RuntimePhase
import dev.kinetic.core.agent.RuntimeRecovery
import dev.kinetic.core.agent.RuntimeState
import dev.kinetic.core.agent.StreamingModelOutput
import dev.kinetic.core.agent.UuidIdGenerator
import dev.kinetic.core.logging.JournalEntry
import dev.kinetic.core.logging.JournalEvent
import dev.kinetic.core.logging.SessionEventJournal
import dev.kinetic.core.context.ContextPlan
import dev.kinetic.core.context.ConversationSummaryService
import dev.kinetic.core.context.SessionSummary
import dev.kinetic.core.context.SummaryCompactionResult
import dev.kinetic.core.memory.ControlledMemoryService
import dev.kinetic.core.memory.ExplicitMemoryCommand
import dev.kinetic.core.memory.ExplicitMemoryCommandParser
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.core.memory.MemoryCommandOperation
import dev.kinetic.core.memory.MemoryResolutionResult
import dev.kinetic.core.memory.MemorySource
import dev.kinetic.core.memory.RememberResult
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.policy.DefaultCapabilityPolicy
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.LedgerBackedApprovalGate
import dev.kinetic.core.policy.PolicyContext
import dev.kinetic.core.session.AgentSession
import dev.kinetic.core.tools.DemoToolCatalog
import dev.kinetic.core.tools.ToolRegistry
import dev.kinetic.core.tools.ToolResult
import dev.kinetic.data.model.AndroidProviderSettingsStore
import dev.kinetic.data.model.CloudProviderConfiguration
import dev.kinetic.data.model.ConfiguredModelProvider
import dev.kinetic.data.model.ProviderMode
import dev.kinetic.data.model.ProviderSettingsSnapshot
import dev.kinetic.data.model.OpenAiConfiguration
import dev.kinetic.data.androidcapabilities.AndroidCapabilityToolCatalog
import dev.kinetic.data.androidcapabilities.ForegroundAndroidCapabilityExecutionCoordinator
import dev.kinetic.data.persistence.KineticPersistence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

data class KernelUiState(
    val runtimeState: RuntimeState = RuntimeState.Idle,
    val approvalRequest: ApprovalRequest? = null,
    val messages: List<AgentMessage> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val streamingText: String? = null,
    val modelOutput: String? = null,
    val toolOutput: String? = null,
    val statusDetail: String? = null,
    val isTurnActive: Boolean = false,
    val isRecovering: Boolean = true,
    val sessionId: String = "",
    val providerSettings: ProviderSettingsSnapshot = ProviderSettingsSnapshot(),
    val settingsError: String? = null,
    val toolActivity: String? = null,
    val userMemories: List<MemoryRecord> = emptyList(),
    val sessionMemories: List<MemoryRecord> = emptyList(),
    val memoryStatus: String? = null,
    val sessionSummary: SessionSummary? = null,
    val contextPlan: ContextPlan? = null,
    val summaryStatus: String? = null,
)

private data class RuntimeUiInputs(
    val runtimeState: RuntimeState,
    val streamingOutput: StreamingModelOutput?,
    val approval: ApprovalRequest?,
    val session: AgentSession?,
    val recoveryComplete: Boolean,
)

private data class MemoryUiInputs(
    val userMemories: List<MemoryRecord>,
    val sessionMemories: List<MemoryRecord>,
    val operationActive: Boolean,
    val status: String?,
)

private data class ContextUiInputs(
    val summary: SessionSummary?,
    val plan: ContextPlan?,
    val operationActive: Boolean,
    val status: String?,
)

private data class MemoryAndContextUiInputs(
    val memory: MemoryUiInputs,
    val context: ContextUiInputs,
)

/** Manual assembly keeps Android persistence, Keystore, and HTTP adapters outside the kernel. */
@OptIn(ExperimentalCoroutinesApi::class)
class KernelViewModel(application: Application) : AndroidViewModel(application) {
    private val clock = Clock.systemUTC()
    private val ids = UuidIdGenerator()
    private val persistence = KineticPersistence.create(application)
    private val sessionStore = persistence.sessionStore
    private val runLedger = persistence.runLedger
    private val memoryRepository = persistence.memoryRepository
    private val summaryRepository = persistence.sessionSummaryRepository
    private val providerSettingsStore = AndroidProviderSettingsStore(application)
    private val localModel = dev.kinetic.data.model.VerifiedLocalModel(
        java.io.File(application.noBackupFilesDir, "local-models"))
    val localModelState = localModel.state
    init { viewModelScope.launch { localModel.initialize() } }
    val localModelStatus = MutableStateFlow(if (localModel.isInstalled())
        "Model data present; integrity is checked before each load." else "Verified Qwen3 GGUF not imported.")
    private val realLocalProvider = dev.kinetic.data.model.LlamaLocalModelProvider(
        localModel, dev.kinetic.data.model.PackagedLlamaEngine(),
        compatible = { android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a") && android.os.Process.is64Bit() },
        memorySafe = {
            val info = android.app.ActivityManager.MemoryInfo()
            application.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
            !info.lowMemory && info.availMem >= 1_060_000L * 1024
        },
    )
    val routingEnvironment = MutableStateFlow(dev.kinetic.core.model.RoutingEnvironment())
    private val mcpHost = dev.kinetic.data.model.mcp.McpHost(dev.kinetic.data.model.mcp.AndroidMcpSettings(application))
    private val appFunctionHost = dev.kinetic.data.model.appfunctions.AppFunctionHost(
        dev.kinetic.data.model.appfunctions.AndroidAppFunctionSettings(application), dev.kinetic.data.model.appfunctions.AndroidAppFunctionCaller(application))
    val appFunctionCatalogs = appFunctionHost.catalogs
    val appFunctionBusy = MutableStateFlow(false)
    val appFunctionStatus = MutableStateFlow("API36+ preview. Android caller eligibility is not assumed. No automatic discovery.")
    val mcpServers = mcpHost.servers
    val mcpBusy = MutableStateFlow(false)
    val mcpStatus = MutableStateFlow("NO_AUTH only. External tools require explicit enable and approval.")
    private val configuredProvider = ConfiguredModelProvider(providerSettingsStore, realLocalProvider = realLocalProvider,
        routingEnvironment = { routingEnvironment.value }, enabledMcpIds = { mcpHost.tools().map { it.definition.id }.toSet() },
        enabledAppFunctionIds = { appFunctionHost.tools().map { it.definition.id }.toSet() })
    val routingDecision = configuredProvider.routingDecision
    val providerMetrics get() = configuredProvider.metrics.records
    val localDeviceFacts = dev.kinetic.data.model.AndroidLocalCapabilityProbe(application).read()
    private val approvalGate = LedgerBackedApprovalGate(runLedger, clock)
    private val journal = SessionEventJournal(sessionStore, ids, clock)
    private val memoryService = ControlledMemoryService(memoryRepository, journal, ids, clock)
    private val summaryService = ConversationSummaryService(
        repository = summaryRepository,
        generator = configuredProvider,
        ids = ids,
        clock = clock,
    )
    private val capabilityDispatcher = ForegroundAndroidCapabilityExecutionCoordinator()
    private val conversationPreferences = application.getSharedPreferences(
        CONVERSATION_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val sessionId = MutableStateFlow(
        conversationPreferences.getString(ACTIVE_SESSION_ID, null)
            ?: ids.nextId("conversation").also(::persistActiveSession),
    )
    private val recoveryComplete = MutableStateFlow(false)
    private val settingsError = MutableStateFlow<String?>(null)
    private val memoryOperationActive = MutableStateFlow(false)
    private val memoryStatus = MutableStateFlow<String?>(null)
    private val summaryOperationActive = MutableStateFlow(false)
    private val summaryStatus = MutableStateFlow<String?>(null)
    private val toolRegistry = ToolRegistry(DemoToolCatalog.create(clock) + AndroidCapabilityToolCatalog.create(capabilityDispatcher))
        .apply { replaceMcp(mcpHost.tools()); replaceAppFunctions(appFunctionHost.tools()) }
    private val runtime = DefaultAgentRuntime(
        modelProvider = configuredProvider,
        toolRegistry = toolRegistry,
        capabilityPolicy = DefaultCapabilityPolicy(),
        policyContext = PolicyContext(DistributionProfile.PLAY_CORE),
        approvalGate = approvalGate,
        sessionStore = sessionStore,
        runLedger = runLedger,
        journal = journal,
        clock = clock,
        idGenerator = ids,
        memoryRepository = memoryRepository,
        sessionSummaryRepository = summaryRepository,
    )

    private var activeTurn: Job? = null
    private fun externalOperationsIdle() = externalCatalogIdle(recoveryComplete.value,
        activeTurn?.isActive == true, summaryOperationActive.value, memoryOperationActive.value,
        mcpBusy.value, appFunctionBusy.value)
    fun addMcpEndpoint(url: String) = manageMcp { add(url) }
    fun addAppFunctionPackage(pkg: String) = manageAppFunctions { add(pkg) }
    fun removeAppFunctionPackage(pkg: String) = manageAppFunctions { remove(pkg) }
    fun refreshAppFunctions(pkg: String) = manageAppFunctions { refresh(pkg) }
    fun enableAppFunction(id: String, enabled: Boolean) = manageAppFunctions { enable(id, enabled) }
    private fun manageAppFunctions(operation: suspend dev.kinetic.data.model.appfunctions.AppFunctionHost.() -> Unit) {
        if (!externalOperationsIdle()) return
        appFunctionBusy.value = true
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { appFunctionHost.operation() }
                appFunctionStatus.value = "Catalog saved. Review each external capability before enabling. Runtime eligibility is not guaranteed."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                appFunctionStatus.value = (failure as? dev.kinetic.core.tools.AppFunctionAdapterException)?.error?.name ?: "APPFUNCTION_CONFIGURATION_INVALID"
            } finally {
                toolRegistry.replaceAppFunctions(appFunctionHost.tools())
                appFunctionBusy.value = false
            }
        }
    }
    fun removeMcpEndpoint(id: String) = manageMcp { remove(id) }
    fun refreshMcpEndpoint(id: String) = manageMcp { refresh(id) }
    fun enableMcpTool(id: String, enabled: Boolean) = manageMcp { enable(id, enabled) }
    fun mcpApprovalDisclosure(id: String): String? {
        if (id.startsWith("appfn_")) {
            val b = appFunctionCatalogs.value.flatMap { it.bindings }.singleOrNull { it.id == id }
                ?: return "AppFunction binding unavailable; execution will fail closed."
            return "External Android AppFunction\nPackage: ${b.descriptor.packageName}\nFunction: ${b.descriptor.functionId}\nSchema: ${b.fingerprint}\nThe target app receives these arguments. Android authorization is still required."
        }
        if (!id.startsWith("mcp_")) return null
        val binding = mcpServers.value.flatMap { it.tools }.singleOrNull { it.id == id }
            ?: return "External MCP binding is no longer available; execution will fail closed."
        return "External HTTPS request to ${binding.endpoint.url}\nRemote tool: ${binding.name}\nSchema: ${binding.schema.fingerprint}\nThe server receives these arguments. Its response has no authority."
    }
    private fun manageMcp(operation: suspend dev.kinetic.data.model.mcp.McpHost.() -> Unit) {
        if (!externalOperationsIdle()) return
        mcpBusy.value = true
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { mcpHost.operation() }
                mcpStatus.value = "Settings saved. Review external tools before enabling."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                mcpStatus.value = (failure as? dev.kinetic.data.model.mcp.McpException)?.code?.name ?: "MCP_SETTINGS_UNAVAILABLE"
            } finally {
                toolRegistry.replaceMcp(mcpHost.tools())
                mcpBusy.value = false
            }
        }
    }
    private val activeSession: Flow<AgentSession?> = sessionId.flatMapLatest(sessionStore::observe)
    private val runtimeInputs = combine(
        runtime.state,
        runtime.streamingOutput,
        approvalGate.pendingRequest,
        activeSession,
        recoveryComplete,
    ) { runtimeState, streaming, approval, session, recovered ->
        RuntimeUiInputs(runtimeState, streaming, approval, session, recovered)
    }
    private val memoryInputs = combine(
        memoryRepository.observeUserMemories(),
        sessionId.flatMapLatest(memoryRepository::observeSessionMemories),
        memoryOperationActive,
        memoryStatus,
    ) { userMemories, sessionMemories, operationActive, status ->
        MemoryUiInputs(userMemories, sessionMemories, operationActive, status)
    }
    private val contextInputs = combine(
        sessionId.flatMapLatest(summaryRepository::observeLatest),
        runtime.contextPlan,
        summaryOperationActive,
        summaryStatus,
    ) { summary, plan, operationActive, status ->
        ContextUiInputs(summary, plan, operationActive, status)
    }
    private val memoryAndContextInputs = combine(memoryInputs, contextInputs) { memory, context ->
        MemoryAndContextUiInputs(memory, context)
    }

    val uiState: StateFlow<KernelUiState> = combine(
        runtimeInputs,
        sessionId,
        providerSettingsStore.settings,
        settingsError,
        memoryAndContextInputs,
    ) { inputs, currentSessionId, providerSettings, configurationError, contextState ->
        reduceKernelUiState(
            runtimeState = inputs.runtimeState,
            approval = inputs.approval,
            session = inputs.session,
            isRecovering = !inputs.recoveryComplete,
            streamingOutput = inputs.streamingOutput,
            sessionId = currentSessionId,
            providerSettings = providerSettings,
            settingsError = configurationError,
            userMemories = contextState.memory.userMemories,
            sessionMemories = contextState.memory.sessionMemories,
            memoryOperationActive = contextState.memory.operationActive,
            memoryStatus = contextState.memory.status,
            sessionSummary = contextState.context.summary,
            contextPlan = contextState.context.plan?.takeIf { it.sessionId == currentSessionId },
            summaryOperationActive = contextState.context.operationActive,
            summaryStatus = contextState.context.status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KernelUiState(sessionId = sessionId.value),
    )

    init {
        activeTurn = viewModelScope.launch {
            try {
                when (val recovery = runtime.recover(sessionId.value)) {
                    is RuntimeRecovery.PendingApproval -> {
                        recoveryComplete.value = true
                        runtime.resumePendingApproval(recovery)
                    }
                    RuntimeRecovery.None,
                    is RuntimeRecovery.Restored,
                    -> recoveryComplete.value = true
                }
            } catch (_: CancellationException) {
                // Cancellation is durably recorded by the runtime when the process remains alive.
            }
        }
    }

    fun sendMessage(content: String) {
        if (!externalOperationsIdle() || content.isBlank()) return
        settingsError.value = null
        val trimmed = content.trim()
        ExplicitMemoryCommandParser.parse(trimmed)?.let { command ->
            rememberExplicitly(trimmed, command)
            return
        }
        activeTurn = viewModelScope.launch {
            try {
                if (providerSettingsStore.current().automaticContextCompactionEnabled) {
                    summaryOperationActive.value = true
                    try {
                        val current = sessionStore.getOrCreate(sessionId.value, clock.instant())
                        applySummaryResult(
                            summaryService.compact(sessionId.value, current.messages, force = false),
                            automatic = true,
                        )
                    } finally {
                        summaryOperationActive.value = false
                    }
                }
                runtime.runTurn(
                    AgentRequest(
                        requestId = ids.nextId("request"),
                        sessionId = sessionId.value,
                        content = trimmed,
                        createdAt = clock.instant(),
                    ),
                )
            } catch (_: CancellationException) {
                // The runtime has already journaled cancellation and published CANCELLED.
            }
        }
    }

    fun startTurn(content: String) = sendMessage(content)

    fun newConversation() {
        if (!externalOperationsIdle()) return
        val next = ids.nextId("conversation")
        persistActiveSession(next)
        sessionId.value = next
        settingsError.value = null
    }

    fun saveCloudConfiguration(
        displayName: String,
        baseUrl: String,
        modelId: String,
        newApiKey: String,
        structuredToolCallingEnabled: Boolean,
    ) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try {
                providerSettingsStore.saveConfiguration(
                    CloudProviderConfiguration(
                        displayName = displayName,
                        baseUrl = baseUrl,
                        modelId = modelId,
                        structuredToolCallingEnabled = structuredToolCallingEnabled,
                    ),
                    newApiKey.takeIf(String::isNotBlank),
                )
                settingsError.value = null
            } catch (failure: IllegalArgumentException) {
                settingsError.value = failure.message ?: "Provider configuration is invalid."
            } catch (_: Throwable) {
                settingsError.value = "Provider configuration could not be saved."
            }
        }
    }

    fun selectProvider(mode: ProviderMode) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try {
                providerSettingsStore.setMode(mode)
                settingsError.value = null
            } catch (_: Throwable) {
                settingsError.value = "Provider mode could not be changed."
            }
        }
    }

    fun configureRouting(mode: dev.kinetic.core.model.RoutingMode, localOnly: Boolean, toolsRequired: Boolean) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try { providerSettingsStore.setRouting(mode, localOnly, toolsRequired); settingsError.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { settingsError.value = "Routing settings could not be saved." }
        }
    }

    fun supplyRoutingNetwork(network: dev.kinetic.core.model.NetworkState) {
        routingEnvironment.value = routingEnvironment.value.copy(network = network)
    }

    private var localImport: Job? = null
    fun deleteLocalModel() {
        if (localImport?.isActive == true) return
        localImport = viewModelScope.launch {
            localModelStatus.value = "Waiting for native work to close before deleting model data."
            try {
                localModel.delete()
                localModelStatus.value = "Model data deleted. History, memories and provider credentials are untouched."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { localModelStatus.value = "Model deletion could not finish. No other app data was targeted." }
        }
    }
    fun importLocalModel(uri: android.net.Uri) {
        if (activeTurn?.isActive == true || localImport?.isActive == true) return
        localImport = viewModelScope.launch {
            localModelStatus.value = "Importing and verifying model data…"
            try {
                localModel.import {
                    require(uri.scheme == "content")
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Model input unavailable")
                }
                localModelStatus.value = "Verified Qwen3-0.6B Q4_0 imported. Select experimental local mode manually."
            } catch (cancelled: CancellationException) {
                localModelStatus.value = "Model import cancelled."
                throw cancelled
            } catch (_: Exception) {
                localModelStatus.value = "Import failed. Select the exact verified Qwen3-0.6B-Q4_0.gguf and ensure sufficient storage."
            }
        }
    }

    fun saveOpenAiConfiguration(configuration: OpenAiConfiguration, newKey: String) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try {
                providerSettingsStore.saveOpenAi(configuration, newKey.takeIf(String::isNotBlank))
                settingsError.value = null
            } catch (_: Throwable) {
                settingsError.value = "OpenAI settings could not be saved. Stored credentials were retained."
            }
        }
    }

    fun clearApiKey(openAi: Boolean = false) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try {
                if (openAi) providerSettingsStore.clearOpenAiKey() else providerSettingsStore.clearApiKey()
                settingsError.value = null
            } catch (_: Throwable) {
                settingsError.value = "The stored API key could not be cleared."
            }
        }
    }

    fun deleteMemory(memoryId: String) = runMemoryOperation { currentSessionId, operationId ->
        val deleted = memoryService.delete(memoryId, currentSessionId, operationId)
        memoryStatus.value = if (deleted) "Memory deleted." else "That memory no longer exists."
    }

    fun replaceMemory(memoryId: String, newValue: String) =
        runMemoryOperation { currentSessionId, operationId ->
            val result = memoryService.replace(
                memoryId,
                newValue,
                MemorySource(currentSessionId, operationId, ids.nextId("memory-ui")),
            )
            memoryStatus.value = when (result) {
                is RememberResult.Replaced -> "Memory updated; ${result.supersededCount} prior version superseded."
                is RememberResult.Duplicate -> "That value is already current."
                RememberResult.NotFound -> "That memory no longer exists."
                is RememberResult.Rejected -> result.userMessage
                else -> "Memory updated."
            }
        }

    fun resolveMemory(memoryId: String) = runMemoryOperation { currentSessionId, operationId ->
        memoryStatus.value = when (
            val result = memoryService.resolve(
                memoryId,
                MemorySource(currentSessionId, operationId, ids.nextId("memory-ui")),
            )
        ) {
            is MemoryResolutionResult.Resolved ->
                "Conflict resolved; ${result.superseded.size} alternative(s) superseded."
            MemoryResolutionResult.NotFound -> "That memory no longer exists."
            MemoryResolutionResult.NotConflicted -> "That memory is not part of an active conflict."
        }
    }

    fun clearSessionMemories() = runMemoryOperation { currentSessionId, operationId ->
        val count = memoryService.clearSession(currentSessionId, operationId)
        memoryStatus.value = "Deleted $count current-session ${if (count == 1) "memory" else "memories"}."
    }

    fun clearAllUserMemory() = runMemoryOperation { currentSessionId, operationId ->
        val count = memoryService.clearUser(currentSessionId, operationId)
        memoryStatus.value = "Deleted $count USER ${if (count == 1) "memory" else "memories"}."
    }

    fun setAutomaticContextCompaction(enabled: Boolean) {
        if (activeTurn?.isActive == true) return
        viewModelScope.launch {
            try {
                providerSettingsStore.setAutomaticContextCompactionEnabled(enabled)
                settingsError.value = null
            } catch (_: Throwable) {
                settingsError.value = "Automatic context compaction setting could not be saved."
            }
        }
    }

    fun compactCurrentSession() {
        if (!externalOperationsIdle()) return
        val currentSessionId = sessionId.value
        activeTurn = viewModelScope.launch {
            summaryOperationActive.value = true
            summaryStatus.value = "Compacting current session context…"
            try {
                val session = sessionStore.getOrCreate(currentSessionId, clock.instant())
                applySummaryResult(summaryService.compact(currentSessionId, session.messages, force = true))
            } catch (_: CancellationException) {
                summaryStatus.value = "Context compaction cancelled; existing context was preserved."
            } catch (_: Throwable) {
                summaryStatus.value = "Kinetic could not compact this session; existing context was preserved."
            } finally {
                summaryOperationActive.value = false
            }
        }
    }

    fun approve() = resolveApproval(ApprovalDecision.APPROVE)
    fun reject() = resolveApproval(ApprovalDecision.REJECT)
    fun cancel() {
        runtime.cancelActiveTurn()
        activeTurn?.cancel()
    }

    fun registerResumedActivity(activity: android.app.Activity) =
        capabilityDispatcher.registerResumedActivity(activity)

    fun unregisterPausedActivity(activity: android.app.Activity) =
        capabilityDispatcher.unregisterPausedActivity(activity)

    override fun onCleared() {
        persistence.close()
        super.onCleared()
    }

    private fun resolveApproval(decision: ApprovalDecision) {
        val approvalId = approvalGate.pendingRequest.value?.approvalId ?: return
        viewModelScope.launch { approvalGate.resolve(approvalId, decision) }
    }

    private fun rememberExplicitly(input: String, command: ExplicitMemoryCommand) {
        val currentSessionId = sessionId.value
        val turnId = ids.nextId("memory-operation")
        val userMessageId = ids.nextId("message")
        activeTurn = viewModelScope.launch {
            memoryOperationActive.value = true
            try {
                sessionStore.getOrCreate(currentSessionId, clock.instant())
                val result = memoryService.remember(
                    command,
                    MemorySource(currentSessionId, turnId, userMessageId),
                )
                val displayedRequest = safeRememberRequestForHistory(input, result, command.operation)
                sessionStore.appendMessage(
                    currentSessionId,
                    AgentMessage(
                        messageId = userMessageId,
                        turnId = turnId,
                        role = MessageRole.USER,
                        content = displayedRequest,
                        createdAt = clock.instant(),
                    ),
                )
                val response = when (result) {
                    is RememberResult.Created ->
                        "Memory saved as ${result.record.scope.name} / ${result.record.category.name}."
                    is RememberResult.Duplicate ->
                        "That exact ${result.record.scope.name} memory is already stored."
                    is RememberResult.Replaced ->
                        "Memory updated; ${result.supersededCount} prior version superseded."
                    is RememberResult.Conflict ->
                        "A conflicting memory was preserved. Resolve the alternatives in Controlled memory."
                    is RememberResult.Forgotten -> "Memory forgotten."
                    RememberResult.NotFound -> "No current memory matched that subject."
                    is RememberResult.Ambiguous ->
                        "That subject has ${result.candidateCount} unresolved memories. Resolve or delete one in Controlled memory."
                    is RememberResult.Rejected -> result.userMessage
                }
                sessionStore.appendMessage(
                    currentSessionId,
                    AgentMessage(
                        messageId = ids.nextId("message"),
                        turnId = turnId,
                        role = MessageRole.ASSISTANT,
                        content = response,
                        createdAt = clock.instant(),
                    ),
                )
                memoryStatus.value = response
            } catch (_: Throwable) {
                memoryStatus.value = "Kinetic could not update memory."
            } finally {
                memoryOperationActive.value = false
            }
        }
    }

    private fun runMemoryOperation(block: suspend (String, String) -> Unit) {
        if (!externalOperationsIdle()) return
        val currentSessionId = sessionId.value
        activeTurn = viewModelScope.launch {
            memoryOperationActive.value = true
            try {
                sessionStore.getOrCreate(currentSessionId, clock.instant())
                block(currentSessionId, ids.nextId("memory-operation"))
            } catch (_: Throwable) {
                memoryStatus.value = "Kinetic could not update memory."
            } finally {
                memoryOperationActive.value = false
            }
        }
    }

    private fun applySummaryResult(result: SummaryCompactionResult, automatic: Boolean = false) {
        summaryStatus.value = when (result) {
            is SummaryCompactionResult.Created ->
                "Session context compacted through message ${result.summary.lastCoveredMessageSequence}."
            is SummaryCompactionResult.NotNeeded -> if (automatic) null else result.reason
            is SummaryCompactionResult.Rejected -> result.userMessage
        }
    }

    private fun persistActiveSession(value: String) {
        conversationPreferences.edit().putString(ACTIVE_SESSION_ID, value).apply()
    }

    private companion object {
        const val CONVERSATION_PREFERENCES = "kinetic_conversation_state"
        const val ACTIVE_SESSION_ID = "active_session_id"
    }
}

internal fun reduceKernelUiState(
    runtimeState: RuntimeState,
    approval: ApprovalRequest?,
    session: AgentSession?,
    isRecovering: Boolean,
    streamingOutput: StreamingModelOutput? = null,
    sessionId: String = session?.sessionId.orEmpty(),
    providerSettings: ProviderSettingsSnapshot = ProviderSettingsSnapshot(),
    settingsError: String? = null,
    userMemories: List<MemoryRecord> = emptyList(),
    sessionMemories: List<MemoryRecord> = emptyList(),
    memoryOperationActive: Boolean = false,
    memoryStatus: String? = null,
    sessionSummary: SessionSummary? = null,
    contextPlan: ContextPlan? = null,
    summaryOperationActive: Boolean = false,
    summaryStatus: String? = null,
): KernelUiState {
    val currentTurnId = runtimeState.turnIdOrNull()
    val modelOutput = currentTurnId?.let { turnId ->
        session?.messages
            ?.lastOrNull { it.turnId == turnId && it.role == MessageRole.ASSISTANT }
            ?.content
    }
    val toolOutput = (runtimeState as? RuntimeState.Completed)
        ?.toolResult
        ?.let { it as? ToolResult.Success }
        ?.output
        ?.displayText()
    val streamText = streamingOutput
        ?.takeIf {
            it.turnId == currentTurnId &&
                runtimeState.phase in setOf(RuntimePhase.THINKING, RuntimePhase.EXECUTING)
        }
        ?.content
        ?.takeIf(String::isNotEmpty)
    return KernelUiState(
        runtimeState = runtimeState,
        approvalRequest = approval,
        messages = session?.messages.orEmpty(),
        journal = session?.journal.orEmpty(),
        streamingText = streamText,
        modelOutput = modelOutput,
        toolOutput = toolOutput,
        statusDetail = when (runtimeState) {
            is RuntimeState.Failed -> runtimeState.error.userMessage
            is RuntimeState.Cancelled -> runtimeState.error.userMessage
            else -> null
        },
        isTurnActive = memoryOperationActive || summaryOperationActive || (
            !isRecovering && runtimeState.phase !in setOf(
                RuntimePhase.IDLE,
                RuntimePhase.COMPLETED,
                RuntimePhase.FAILED,
                RuntimePhase.CANCELLED,
            )
            ),
        isRecovering = isRecovering,
        sessionId = sessionId,
        providerSettings = providerSettings,
        settingsError = settingsError,
        userMemories = userMemories,
        sessionMemories = sessionMemories,
        memoryStatus = memoryStatus,
        sessionSummary = sessionSummary,
        contextPlan = contextPlan,
        summaryStatus = summaryStatus,
        toolActivity = currentTurnId?.let { turnId ->
            val events = session?.journal.orEmpty().filter { it.turnId == turnId }
            val requested = events.mapNotNull { it.event as? JournalEvent.ToolRequested }.lastOrNull()
            requested?.let {
                val approvalRequired = events.any { entry ->
                    entry.event is JournalEvent.ApprovalRequested
                }
                "Requested ${it.toolId} · ${it.inputSummary} · " +
                    if (approvalRequired) "CONFIRM approval" else "SAFE / no approval"
            }
        },
    )
}

private fun RuntimeState.turnIdOrNull(): String? = when (this) {
    RuntimeState.Idle -> null
    is RuntimeState.Thinking -> turnId
    is RuntimeState.WaitingForApproval -> turnId
    is RuntimeState.Executing -> turnId
    is RuntimeState.Completed -> turnId
    is RuntimeState.Failed -> turnId
    is RuntimeState.Cancelled -> turnId
}

internal fun safeRememberRequestForHistory(
    input: String,
    result: RememberResult,
    operation: MemoryCommandOperation = MemoryCommandOperation.CREATE,
): String =
    if (result is RememberResult.Rejected) {
        "Remember request rejected: sensitive or invalid content omitted."
    } else if (operation == MemoryCommandOperation.FORGET) {
        "Explicit forget request processed; subject text omitted from history."
    } else {
        input
    }
