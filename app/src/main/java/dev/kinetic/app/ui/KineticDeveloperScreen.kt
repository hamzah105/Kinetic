package dev.kinetic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import dev.kinetic.core.model.ModelProfile
import dev.kinetic.data.model.OpenAiConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.kinetic.app.KernelViewModel
import dev.kinetic.app.KernelUiState
import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.agent.MessageRole
import dev.kinetic.core.logging.JournalEntry
import dev.kinetic.core.memory.MAX_MEMORY_UI_PREVIEW_LENGTH
import dev.kinetic.core.memory.MemoryRecord
import dev.kinetic.data.model.ProviderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticDeveloperScreen(viewModel: KernelViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var surface by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val count = uiState.messages.size
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(count, uiState.approvalRequest?.approvalId) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val wide = stellarLayout(maxWidth.value, LocalDensity.current.fontScale) == StellarLayout.EXPANDED
            val shortViewport = stellarNeedsVerticalOverflow(maxHeight.value, LocalDensity.current.fontScale)
            val supporting = wide && surface in listOf("Memory", "Context")
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(
                    Modifier.weight(1f).widthIn(max = 820.dp).fillMaxHeight()
                        .then(if (shortViewport) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Kinetic", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        StellarMark()
                        TextButton(onClick = { surface = "Menu" }) { Text("Menu") }
                    }
                    TextButton(onClick = { surface = "Provider" }, modifier = Modifier.fillMaxWidth()) {
                        Text(providerStatus(uiState))
                    }
                    // A landscape IME can leave less height than the fixed header/composer.
                    // Allow the whole column to scroll, with a bounded nested conversation,
                    // instead of measuring the composer and actions out of the viewport.
                    LazyColumn(state = listState, modifier = Modifier
                        .then(if (shortViewport) Modifier.height(160.dp) else Modifier.weight(1f))
                        .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uiState.messages.isEmpty()) item {
                            Column(Modifier.padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("A little clarity.\nA deliberate next step.", style = MaterialTheme.typography.headlineMedium)
                                Text("Ask, explore, or choose an action. You stay in control.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(uiState.messages, key = { it.messageId }) { MessageBubble(it) }
                        uiState.streamingText?.let { partial -> item {
                            MessageBubble(AgentMessage("streaming", "streaming", MessageRole.ASSISTANT,
                                partial, java.time.Instant.EPOCH), isStreaming = true)
                        } }
                        if (uiState.isRecovering) item { InfoCard("Restoring", "Reading durable conversation state") }
                        uiState.statusDetail?.let { item { InfoCard("Status", it) } }
                        uiState.summaryStatus?.let { item { InfoCard("Context", it) } }
                        uiState.toolActivity?.let { item { InfoCard("Action", it) } }
                        uiState.approvalRequest?.let { approval -> item(key = approval.approvalId) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Approval required", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() })
                                    Text(approval.toolCall.toolId.replace('_', ' '), fontWeight = FontWeight.Bold)
                                    viewModel.mcpApprovalDisclosure(approval.toolCall.toolId)?.let { Text(it) }
                                    Text(approval.toolCall.input.approvalSummary())
                                    Text(actionDisclosure(approval.toolCall.toolId), style = MaterialTheme.typography.bodySmall)
                                    Text("Nothing runs until you approve these exact arguments.", style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = viewModel::reject) { Text("Reject") }
                                        Button(onClick = viewModel::approve) { Text("Approve") }
                                    }
                                }
                            }
                        } }
                    }
                    if (uiState.isTurnActive) Text(
                        if (uiState.approvalRequest != null) "Waiting for approval" else uiState.runtimeState.phase.name.lowercase().replace('_', ' '),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        label = { Text("Message Kinetic") }, modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isTurnActive && !uiState.isRecovering, minLines = 1, maxLines = 3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { keyboard?.hide(); viewModel.sendMessage(input); input = "" },
                            enabled = input.isNotBlank() && !uiState.isTurnActive && !uiState.isRecovering) { Text("Send") }
                        if (uiState.isTurnActive) OutlinedButton(onClick = viewModel::cancel) { Text("Cancel") }
                        TextButton(onClick = viewModel::newConversation,
                            enabled = !uiState.isTurnActive && !uiState.isRecovering) { Text("New conversation") }
                    }
                }
                if (supporting) Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TextButton(onClick = { surface = null }) { Text("Close supporting pane") }
                    SupportingSurface(surface!!, uiState, viewModel)
                }
            }
            if (surface != null && !supporting) ModalBottomSheet(onDismissRequest = { surface = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { surface = null }) { Text("Back to conversation") }
                    if (surface == "Menu") {
                        Text("Your Kinetic", style = MaterialTheme.typography.headlineSmall)
                        listOf("Provider", "Memory", "Context", "Appearance", "Debug").forEach { destination ->
                            OutlinedButton(onClick = { surface = destination }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (destination == "Context") "Why Kinetic knows this" else destination)
                            }
                        }
                    } else SupportingSurface(surface!!, uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun SupportingSurface(name: String, uiState: KernelUiState, viewModel: KernelViewModel) {
    when (name) {
        "Provider" -> ProviderSettingsPanel(uiState, viewModel)
        "Memory" -> MemoryPanel(uiState, viewModel)
        "Context" -> {
            Text("Why Kinetic knows this", style = MaterialTheme.typography.headlineSmall)
            Text("Deterministic context metadata — not the model's private reasoning.")
            DeveloperPanel(uiState, viewModel, showJournal = false)
        }
        "Debug" -> {
            Text("Local AI · development", style = MaterialTheme.typography.titleMedium)
            val facts = viewModel.localDeviceFacts
            Text("Android ${facts.apiLevel} · ${facts.supportedAbis.joinToString()}")
            Text("RAM bytes: ${facts.physicalRamBytes ?: "unknown"} · heap class MiB: ${facts.memoryClassMiB ?: "unknown"}")
            Text("Available storage bytes: ${facts.availableStorageBytes ?: "unknown"}")
            Text("${if (facts.emulatorLikely) "Emulator detected. " else ""}Emulator results are not physical-phone performance evidence.")
            Text("SIMULATED / TEST: available; no model file required. AICore, LiteRT-LM and llama.cpp adapters are not installed; device/model support is unprobed, not declared unsupported.")
            Text("Benchmark: NOT RUN. Real load time, tokens/s, RAM, battery, thermal and quality measurements unavailable. Physical supported ARM64 device required.")
            HorizontalDivider()
            DeveloperPanel(uiState, viewModel)
            val metrics by viewModel.providerMetrics.collectAsState()
            Text("Local request metrics", style = MaterialTheme.typography.titleMedium)
            Text("Last 20 requests, in memory only. No prompts or credentials. Costs are approximate standard-tier USD.")
            metrics.asReversed().forEach { metric ->
                Text("${metric.provider} · ${metric.modelId} · ${metric.profile ?: "default"} · ${metric.outcome}")
                Text("${metric.durationMs} ms · first text ${metric.firstTokenMs ?: "unavailable"} ms")
                Text("Tokens: input ${metric.inputTokens ?: "unknown"}, cached ${metric.cachedTokens ?: "unknown"}, output ${metric.outputTokens ?: "unknown"}")
                Text("Approx. USD: ${metric.approximateUsd?.let { String.format(java.util.Locale.ROOT, "%.6f", it) } ?: "unavailable"}")
                HorizontalDivider()
            }
        }
        "Appearance" -> {
            val appearance = LocalAppearance.current
            Text("Kinetic Stellar", style = MaterialTheme.typography.headlineSmall)
            StellarTheme.entries.forEach { choice ->
                OutlinedButton(onClick = { appearance.change(choice, appearance.dynamic) }, modifier = Modifier.fillMaxWidth()) {
                    Text(choice.label + if (appearance.theme == choice) " · selected" else "")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = appearance.dynamic, onCheckedChange = { appearance.change(appearance.theme, it) },
                    modifier = Modifier.semantics { contentDescription = "Android dynamic color" })
                Text("Android dynamic color (Android 12+)")
            }
            Text("Static decorative stars; no particle animation. System font size and keyboard navigation are supported.")
        }
    }
}

@Composable
private fun StellarMark() {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(40.dp).clearAndSetSemantics { }) {
        val points = listOf(0.25f to 0.28f, 0.72f to 0.17f, 0.6f to 0.72f)
        points.forEachIndexed { i, (x, y) -> drawCircle(accent.copy(alpha = 0.45f + i * 0.2f),
            radius = (if (i == 0) 2.4f else 1.5f).dp.toPx(), center = Offset(size.width * x, size.height * y)) }
    }
}

internal enum class StellarLayout { COMPACT, MEDIUM, EXPANDED }
internal fun stellarNeedsVerticalOverflow(heightDp: Float, fontScale: Float): Boolean =
    heightDp < 360f * fontScale.coerceAtLeast(1f)

internal fun stellarLayout(widthDp: Float, fontScale: Float): StellarLayout = when {
    widthDp < 600 || fontScale >= 1.5f -> StellarLayout.COMPACT
    widthDp < 840 -> StellarLayout.MEDIUM
    else -> StellarLayout.EXPANDED
}

internal fun actionDisclosure(toolId: String): String = when (toolId) {
    "open_https_url" -> "The URL leaves Kinetic for your browser; the site may receive a network request. Not automatically reversible."
    "share_text" -> "Text leaves Kinetic for Android's chooser. You choose a recipient or cancel; Kinetic sends nothing."
    "compose_email" -> "These fields leave Kinetic for an email composer. You review, send or discard the draft."
    "open_dialer" -> "The number leaves Kinetic for the dialer. No call is placed; you may close it."
    "copy_text_to_clipboard" -> "Text is written to the system clipboard and may be pasted into other apps. It replaces the prior clip; no automatic undo."
    "open_settings" -> "Opens the selected Android Settings page. No setting is changed; close it to return."
    else -> "Harmless local demo only. No data leaves Kinetic and no device setting changes."
}

@Composable
private fun MemoryPanel(uiState: KernelUiState, viewModel: KernelViewModel) {
    var confirmClearUser by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Controlled memory", fontWeight = FontWeight.Bold)
            Text(
                "Use “Remember that …” for USER memory or “Remember for this conversation that …” " +
                    "for SESSION memory. Memory is context only; it never grants tool approval.",
                style = MaterialTheme.typography.bodySmall,
            )
            uiState.memoryStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            MemoryLifecycleSections("USER memory", uiState.userMemories, viewModel)
            if (uiState.userMemories.isNotEmpty()) {
                if (confirmClearUser) {
                    Text("Delete all USER memories? Conversation history and provider settings stay intact.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.clearAllUserMemory()
                                confirmClearUser = false
                            },
                            enabled = !uiState.isTurnActive,
                        ) { Text("Confirm clear USER memory") }
                        OutlinedButton(onClick = { confirmClearUser = false }) { Text("Cancel") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmClearUser = true },
                        enabled = !uiState.isTurnActive,
                    ) { Text("Clear all USER memory") }
                }
            }

            HorizontalDivider()
            MemoryLifecycleSections("Current SESSION memory", uiState.sessionMemories, viewModel)
            if (uiState.sessionMemories.isNotEmpty()) {
                OutlinedButton(
                    onClick = viewModel::clearSessionMemories,
                    enabled = !uiState.isTurnActive,
                ) { Text("Clear current SESSION memory") }
            }
        }
    }
}

@Composable
private fun MemoryLifecycleSections(title: String, memories: List<MemoryRecord>, viewModel: KernelViewModel) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    listOf("NEEDS ATTENTION" to "CONFLICTED", "CURRENT" to "ACTIVE", "HISTORY" to "SUPERSEDED").forEach { (label, status) ->
        val group = memories.filter { it.retentionState.name == status }
        if (group.isNotEmpty()) MemorySection(label, group, viewModel)
    }
    if (memories.isEmpty()) Text("None stored.")
}

@Composable
private fun MemorySection(
    title: String,
    memories: List<MemoryRecord>,
    viewModel: KernelViewModel,
) {
    Text("$title (${memories.size})", style = MaterialTheme.typography.titleSmall)
    if (memories.isEmpty()) {
        Text("None stored.", style = MaterialTheme.typography.bodySmall)
    } else {
        groupMemoryHistory(memories).forEach { ordered ->
            ordered.first().governanceSubject?.let { subject ->
                Text(subject, fontWeight = FontWeight.SemiBold)
            }
            ordered.forEach { memory ->
                var editing by rememberSaveable(memory.memoryId) { mutableStateOf(false) }
                val actions = memoryUiActions(memory)
                var replacementValue by rememberSaveable(memory.memoryId) {
                    mutableStateOf(memory.governanceValue.orEmpty())
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${memory.retentionState.name} · ${memory.category.name} · " +
                                memory.provenance.name,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(memory.content.safeMemoryPreview())
                        Text(
                            "Updated ${memory.updatedAt} · source ${memory.sourceSessionId.take(12)}… / " +
                                memory.sourceTurnId.take(12) + "…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        memory.governanceKey?.let {
                            Text(
                                "Governance key ${it.take(12)}…",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (editing) {
                            OutlinedTextField(
                                value = replacementValue,
                                onValueChange = { replacementValue = it },
                                label = { Text("Replacement value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.replaceMemory(memory.memoryId, replacementValue)
                                        editing = false
                                    },
                                    enabled = replacementValue.isNotBlank(),
                                ) { Text("Replace") }
                                OutlinedButton(onClick = { editing = false }) { Text("Cancel") }
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (MemoryUiAction.EDIT_REPLACE in actions) {
                                    OutlinedButton(onClick = { editing = true }) {
                                        Text("Edit / replace")
                                    }
                                }
                                if (MemoryUiAction.RESOLVE in actions) {
                                    Button(onClick = { viewModel.resolveMemory(memory.memoryId) }) {
                                        Text("Use this value")
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.deleteMemory(memory.memoryId) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

internal enum class MemoryUiAction {
    EDIT_REPLACE,
    RESOLVE,
    DELETE,
}

internal fun memoryUiActions(memory: MemoryRecord): Set<MemoryUiAction> = buildSet {
    if (memory.retentionState.name == "ACTIVE" && memory.governanceKey != null) {
        add(MemoryUiAction.EDIT_REPLACE)
    }
    if (memory.retentionState.name == "CONFLICTED") add(MemoryUiAction.RESOLVE)
    add(MemoryUiAction.DELETE)
}

internal fun groupMemoryHistory(memories: List<MemoryRecord>): List<List<MemoryRecord>> = memories
    .groupBy { it.governanceKey ?: it.memoryId }
    .values
    .map { history ->
        history.sortedWith(compareByDescending<MemoryRecord> { it.updatedAt }.thenBy { it.memoryId })
    }
    .sortedWith(
        compareByDescending<List<MemoryRecord>> { it.first().updatedAt }
            .thenBy { it.first().memoryId },
    )

private fun String.safeMemoryPreview(): String {
    val singleLine = replace(Regex("[\\r\\n\\t]+"), " ").trim()
    return if (singleLine.length <= MAX_MEMORY_UI_PREVIEW_LENGTH) {
        singleLine
    } else {
        singleLine.take(MAX_MEMORY_UI_PREVIEW_LENGTH) + "…"
    }
}

@Composable
private fun ProviderSettingsPanel(uiState: KernelUiState, viewModel: KernelViewModel) {
    val localStatus by viewModel.localModelStatus.collectAsState()
    val localState by viewModel.localModelState.collectAsState()
    val routingDecision by viewModel.routingDecision.collectAsState()
    val routingEnvironment by viewModel.routingEnvironment.collectAsState()
    var confirmModelDelete by remember { mutableStateOf(false) }
    val modelPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importLocalModel) }
    val settings = uiState.providerSettings
    var openAi by rememberSaveable { mutableStateOf(settings.mode == ProviderMode.OPENAI) }
    val stored = settings.cloud
    var displayName by remember(stored.displayName) { mutableStateOf(stored.displayName) }
    var baseUrl by remember(stored.baseUrl) { mutableStateOf(stored.baseUrl) }
    var modelId by remember(stored.modelId) { mutableStateOf(stored.modelId) }
    // Deliberately NOT saveable: credentials never enter saved-instance state.
    var apiKey by remember(openAi) { mutableStateOf("") }
    var structuredTools by remember(openAi, stored.structuredToolCallingEnabled, settings.openAi.structuredTools) {
        mutableStateOf(if (openAi) settings.openAi.structuredTools else stored.structuredToolCallingEnabled)
    }
    var profile by remember(settings.openAi.profile) { mutableStateOf(settings.openAi.profile) }
    var confirmClear by remember(openAi) { mutableStateOf(false) }
    val hasKey = if (openAi) settings.hasOpenAiKey else settings.hasApiKey
    val enabled = !uiState.isTurnActive && !uiState.isRecovering

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Models & privacy", style = MaterialTheme.typography.headlineSmall)
        McpSettingsPanel(uiState, viewModel)
        AppFunctionsSettingsPanel(uiState, viewModel)
        Text("Active: ${providerStatus(uiState)}")
        Text("Router mode: ${settings.routingMode}. Hybrid is experimental; no hidden retry or fallback.")
        Text("Hybrid may send selected conversation context to a configured cloud provider. Enable private / local-only to prohibit cloud.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dev.kinetic.core.model.RoutingMode.entries.forEach { mode ->
                OutlinedButton(onClick = { viewModel.configureRouting(mode, settings.routingLocalOnly, settings.routingToolsRequired) }, enabled = enabled) {
                    Text("Use $mode routing")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.routingLocalOnly, onCheckedChange = {
                viewModel.configureRouting(settings.routingMode, it, settings.routingToolsRequired)
            }, enabled = enabled)
            Text("Private / local-only: prohibit cloud")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.routingToolsRequired, onCheckedChange = {
                viewModel.configureRouting(settings.routingMode, settings.routingLocalOnly, it)
            }, enabled = enabled)
            Text("Require structured tool proposals (not execution approval)")
        }
        Text("Network metadata: ${routingEnvironment.network} (user supplied, not measured; resets on app restart). Battery/thermal: UNKNOWN.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dev.kinetic.core.model.NetworkState.entries.forEach { network ->
                OutlinedButton(onClick = { viewModel.supplyRoutingNetwork(network) }, enabled = enabled) { Text(network.name) }
            }
        }
        routingDecision?.let { decision ->
            Text("Last routing decision: ${decision.selected ?: "NONE"} / ${decision.reason}. Cloud prohibited: ${decision.cloudProhibited}; local unavailable: ${decision.localUnavailable}.")
            Text("Constraints: ${decision.requirements}; supplied environment: ${decision.environment}", style = MaterialTheme.typography.bodySmall)
            decision.candidates.forEach {
                Text("${it.candidate.provider}: ${if (it.eligible) "ELIGIBLE FOR ATTEMPT" else it.rejections.joinToString()} / availability ${it.candidate.availability} / context ${it.candidate.maxContextTokens ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Experimental local · Qwen3-0.6B Q4_0 / llama.cpp. Text only, 1024 context, 64-token output. Device integration validation deferred; not a production default.")
        Text(localStatus, style = MaterialTheme.typography.bodySmall)
        Text("Model state: $localState · runtime llama.cpp v0.4.0", style = MaterialTheme.typography.bodySmall)
        Text("GGUF revision: ${dev.kinetic.data.model.QwenCandidate.REVISION}\nSHA-256: ${dev.kinetic.data.model.QwenCandidate.SHA256}\nBytes: ${dev.kinetic.data.model.QwenCandidate.BYTES}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { confirmModelDelete = true }, enabled = enabled) { Text("Delete local model data") }
        if (confirmModelDelete) {
            Text("Delete only the imported GGUF? Conversations, memories and keys are retained. Active native work must close first.")
            OutlinedButton(onClick = { confirmModelDelete = false; viewModel.deleteLocalModel() }) { Text("Confirm model deletion") }
            OutlinedButton(onClick = { confirmModelDelete = false }) { Text("Keep model") }
        }
        OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = enabled) {
            Text("Import verified GGUF model data")
        }
        OutlinedButton(onClick = { viewModel.selectProvider(ProviderMode.LOCAL_LLAMA) }, enabled = enabled) {
            Text("Use Local · Qwen3 experimental")
        }
        Text("Separate simulated test backend · no real inference in this mode.")
        OutlinedButton(onClick = { viewModel.selectProvider(ProviderMode.LOCAL_SIMULATED) }, enabled = enabled) {
            Text("Use Local · SIMULATED / TEST")
        }
        Text("Local test prompts: hello, echo: hello, protected demo, open https://example.com, local unavailable, local malformed. Device details in Debug.",
            style = MaterialTheme.typography.bodySmall)
        Text(if (settings.mode == ProviderMode.OPENAI) "OpenAI · ${settings.openAi.modelId} · ${settings.openAi.profile} · tools ${settings.openAi.structuredTools}"
            else if (settings.mode == ProviderMode.CLOUD) "Compatible endpoint · ${settings.cloud.modelId} · tools ${settings.cloud.structuredToolCallingEnabled}"
            else if (settings.mode == ProviderMode.LOCAL_SIMULATED) "SIMULATED / TEST · offline · no real LLM"
            else if (settings.mode == ProviderMode.LOCAL_LLAMA) "Qwen3 experimental · local text only · no tools or cloud fallback"
            else "Fake · offline · deterministic tools")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openAi = true }, enabled = enabled) { Text("OpenAI settings") }
            OutlinedButton(onClick = { openAi = false }, enabled = enabled) { Text("OpenRouter / compatible") }
        }
        if (openAi) {
            Text("OpenAI · gpt-6-astra", style = MaterialTheme.typography.titleMedium)
            Text("Selected context leaves the device. Responses storage is disabled (store=false). OpenAI safety monitoring and prompt-cache retention may still apply. Room remains canonical.",
                style = MaterialTheme.typography.bodySmall)
            Text("Astra access depends on your OpenAI account. Enter your OpenAI key here only; an OpenRouter key is never reused.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelProfile.entries.forEach { choice ->
                    OutlinedButton(onClick = { profile = choice }, enabled = enabled) {
                        Text(choice.name.lowercase().replaceFirstChar(Char::titlecase) + if (choice == profile) " · selected" else "")
                    }
                }
            }
            Text("Fast = low effort (default); Balanced = medium; Deep = high. Higher effort can cost more and take longer.")
        } else {
            Text("OpenRouter / compatible", style = MaterialTheme.typography.titleMedium)
            Text("Selected context is sent to your configured HTTPS endpoint. Retention and pricing depend on that provider. Reasoning profiles are not negotiated for arbitrary models.")
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Provider display name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("HTTPS base URL") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
            OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("Model ID") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        }
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
            label = { Text(if (hasKey) "Replace API key" else "API key") },
            supportingText = { Text(if (hasKey) "Stored key retained. Blank input keeps it." else "No key stored for this provider.") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            singleLine = true, enabled = enabled,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = structuredTools, onCheckedChange = { structuredTools = it }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Kinetic structured tools" })
            Text("Kinetic structured tools · actions still require approval")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (openAi) viewModel.saveOpenAiConfiguration(OpenAiConfiguration(profile = profile, structuredTools = structuredTools), apiKey)
                else viewModel.saveCloudConfiguration(displayName, baseUrl, modelId, apiKey, structuredTools)
                apiKey = ""
            }, enabled = enabled) { Text("Save settings") }
            OutlinedButton(onClick = { viewModel.selectProvider(if (openAi) ProviderMode.OPENAI else ProviderMode.CLOUD) },
                enabled = enabled) { Text(if (openAi) "Use OpenAI" else "Use Cloud") }
            OutlinedButton(onClick = { viewModel.selectProvider(ProviderMode.FAKE) }, enabled = enabled) { Text("Use Fake") }
        }
        Text("Save settings before selecting a provider. Blank saves and switching never delete a key.")
        if (confirmClear) {
            Text("Clear only this ${if (openAi) "OpenAI" else "compatible"} credential? The other provider and all history remain unchanged.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.clearApiKey(openAi); confirmClear = false }, enabled = enabled) { Text("Confirm clear key") }
                OutlinedButton(onClick = { confirmClear = false }) { Text("Keep key") }
            }
        } else OutlinedButton(onClick = { confirmClear = true }, enabled = enabled && hasKey) { Text("Clear API key") }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.automaticContextCompactionEnabled,
                onCheckedChange = viewModel::setAutomaticContextCompaction, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Automatic context compaction" })
            Text("Automatic context compaction")
        }
        Text("Default OFF. Foreground compaction may incur provider cost.", style = MaterialTheme.typography.bodySmall)
        uiState.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MessageBubble(message: AgentMessage, isStreaming: Boolean = false) {
    val title = when (message.role) {
        MessageRole.USER -> "You"
        MessageRole.ASSISTANT -> if (isStreaming) "Kinetic · generating" else "Kinetic"
        MessageRole.TOOL -> "Tool result"
    }
    val color = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageRole.ASSISTANT -> MaterialTheme.colorScheme.secondaryContainer
        MessageRole.TOOL -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(message.content)
        }
    }
}

@Composable
private fun DeveloperPanel(uiState: KernelUiState, viewModel: KernelViewModel, showJournal: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Developer state", fontWeight = FontWeight.Bold)
            Text("Runtime: ${uiState.runtimeState.phase}")
            Text("Session: ${uiState.sessionId}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = viewModel::compactCurrentSession,
                enabled = !uiState.isTurnActive && !uiState.isRecovering,
            ) { Text("Compact current session context now") }
            uiState.sessionSummary?.let { summary ->
                Text("Session summary", fontWeight = FontWeight.Bold)
                Text(
                    "Coverage ${summary.firstCoveredMessageSequence}.." +
                        "${summary.lastCoveredMessageSequence} · ${summary.sourceMessageCount} source messages · " +
                        summary.provenance.name,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(summary.content.safeContextPreview(), style = MaterialTheme.typography.bodySmall)
            } ?: Text("Session summary: none", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Context Inspector", fontWeight = FontWeight.Bold)
            uiState.contextPlan?.let { plan ->
                val summaryLabel = plan.summary?.let { summary ->
                    "used ${summary.firstCoveredMessageSequence}..${summary.lastCoveredMessageSequence}"
                } ?: "not used"
                Text(
                    "Budget ${plan.estimatedInputTokens} input + ${plan.reservedOutputTokens} output " +
                        "= ${plan.totalEstimatedTokens}/${plan.budgetLimitTokens} estimated tokens",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Memories ${plan.memoryDiagnostics.size}/${plan.memoriesConsidered} selected · " +
                        "Summary $summaryLabel · " +
                        "Messages ${plan.selectedMessages.size} selected, " +
                        "${plan.omittedCoveredMessages} covered, ${plan.omittedBudgetMessages} budget-omitted",
                    style = MaterialTheme.typography.bodySmall,
                )
                plan.memoryDiagnostics.forEach { diagnostic ->
                    Text(
                        "${diagnostic.memoryId.take(12)}… · ${diagnostic.scope}/${diagnostic.category} · " +
                            "${diagnostic.retentionState} · key " +
                            "${diagnostic.governanceKeyHashPrefix ?: "none"} · " +
                            "score ${diagnostic.score} · ${diagnostic.matchedTermCount} matched terms",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (plan.omissionReasons.isNotEmpty()) {
                    Text(
                        "Omissions: ${plan.omissionReasons.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } ?: Text("No context plan has run for this session.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            if (showJournal) uiState.journal.takeLast(4).asReversed().forEach { JournalRow(it) }
        }
    }
}

private fun String.safeContextPreview(): String {
    val singleLine = replace(Regex("[\\r\\n\\t]+"), " ").trim()
    return if (singleLine.length <= MAX_MEMORY_UI_PREVIEW_LENGTH) singleLine
    else singleLine.take(MAX_MEMORY_UI_PREVIEW_LENGTH) + "…"
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun JournalRow(entry: JournalEntry) {
    Text(
        text = "#${entry.sequence} ${entry.event}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

private fun providerStatus(uiState: KernelUiState): String = if (
    uiState.providerSettings.routingMode == dev.kinetic.core.model.RoutingMode.HYBRID
) "Hybrid · inspect Router for the selected backend" else when (uiState.providerSettings.mode) {
    ProviderMode.OPENAI -> "Astra · " + uiState.providerSettings.openAi.profile.name.lowercase().replaceFirstChar(Char::titlecase)
    ProviderMode.FAKE -> "Kinetic · Offline"
    ProviderMode.LOCAL_SIMULATED -> "Local · SIMULATED / TEST"
    ProviderMode.LOCAL_LLAMA -> "Local · Qwen3 experimental"
    ProviderMode.CLOUD -> buildString {
        append("Cloud")
        append(" · ")
        append(if (uiState.providerSettings.hasApiKey) "Ready" else "Setup needed")
    }
}
