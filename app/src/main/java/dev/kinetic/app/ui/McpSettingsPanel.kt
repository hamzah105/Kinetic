package dev.kinetic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kinetic.app.KernelUiState
import dev.kinetic.app.KernelViewModel

@Composable
internal fun McpSettingsPanel(state: KernelUiState, viewModel: KernelViewModel) {
    val servers by viewModel.mcpServers.collectAsState()
    val busy by viewModel.mcpBusy.collectAsState()
    val status by viewModel.mcpStatus.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    // Not saveable: mistakenly pasted credentials must not enter saved state.
    var endpoint by remember { mutableStateOf("") }
    val enabled = !busy && !state.isTurnActive && !state.isRecovering
    OutlinedButton(onClick = { expanded = !expanded }) { Text("External MCP tools (${servers.size}/4)") }
    if (!expanded) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("External capabilities, not trusted instructions. Public HTTPS, no authentication. Never paste keys into endpoints or arguments. Every invocation needs approval. Cloud structured tools must be enabled to propose MCP actions.")
        Text(status)
        OutlinedTextField(endpoint, { if (it.length <= 2048) endpoint = it }, label = { Text("Public HTTPS endpoint (no secrets or query)") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { viewModel.addMcpEndpoint(endpoint); endpoint = "" }, enabled = enabled && servers.size < 4 && endpoint.isNotBlank()) { Text("Add endpoint") }
        servers.forEach { server ->
            Text(server.endpoint.url, style = MaterialTheme.typography.titleSmall)
            Text("${server.status} · NO_AUTH")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.refreshMcpEndpoint(server.endpoint.serverId) }, enabled = enabled) { Text("Refresh catalog") }
                OutlinedButton(onClick = { viewModel.removeMcpEndpoint(server.endpoint.serverId) }, enabled = enabled) { Text("Remove") }
            }
            server.tools.forEach { tool ->
                var preview by remember(tool.id) { mutableStateOf(false) }
                Text("Untrusted remote tool: ${tool.name}")
                Text("Schema: ${tool.schema.fingerprint.take(16)} · always CONFIRM")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(tool.enabled, { viewModel.enableMcpTool(tool.id, it) }, enabled = enabled)
                    TextButton(onClick = { preview = !preview }) { Text("Inspect schema / description") }
                }
                if (preview) {
                    Text("Untrusted description: ${tool.description}")
                    Text(tool.schema.preview)
                }
            }
        }
    }
}
