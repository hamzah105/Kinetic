package dev.kinetic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kinetic.app.KernelUiState
import dev.kinetic.app.KernelViewModel

@Composable
internal fun AppFunctionsSettingsPanel(state: KernelUiState, viewModel: KernelViewModel) {
    val catalogs by viewModel.appFunctionCatalogs.collectAsState()
    val busy by viewModel.appFunctionBusy.collectAsState()
    val mcpBusy by viewModel.mcpBusy.collectAsState()
    val status by viewModel.appFunctionStatus.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var pkg by remember { mutableStateOf("") }
    val enabled = !busy && !mcpBusy && !state.isTurnActive && !state.isRecovering
    OutlinedButton(onClick = { expanded = !expanded }) { Text("Android AppFunctions (${catalogs.size}/4)") }
    if (!expanded) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Preview caller only. External functions execute in other apps and always require Kinetic approval. Android may deny caller eligibility. No broad package visibility or fallback. Never enter credentials.")
        Text(status)
        OutlinedTextField(pkg, { if (it.length <= 200) pkg = it }, label = { Text("Explicit target package") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { viewModel.addAppFunctionPackage(pkg); pkg = "" }, enabled = enabled && pkg.isNotBlank() && catalogs.size < 4) { Text("Add target") }
        catalogs.forEach { catalog ->
            Text(catalog.packageName, style = MaterialTheme.typography.titleSmall)
            Text("${catalog.availability}: ${catalog.status}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.refreshAppFunctions(catalog.packageName) }, enabled = enabled) { Text("Discover / refresh") }
                OutlinedButton(onClick = { viewModel.removeAppFunctionPackage(catalog.packageName) }, enabled = enabled) { Text("Remove") }
            }
            catalog.bindings.forEach { binding ->
                var preview by remember(binding.id) { mutableStateOf(false) }
                Text(binding.descriptor.functionId)
                Text("Platform enabled at discovery: ${binding.descriptor.platformEnabled}; checked again before execution")
                Text("Fingerprint: ${binding.fingerprint}")
                Row {
                    Switch(binding.enabled, { viewModel.enableAppFunction(binding.id, it) }, enabled = enabled && binding.descriptor.platformEnabled)
                    TextButton(onClick = { preview = !preview }) { Text("Inspect untrusted metadata") }
                }
                if (preview) {
                    Text("Untrusted description: ${binding.descriptor.description}")
                    binding.descriptor.parameters.forEach { Text("${it.name}: ${it.type}, required=${it.required}, enum=${it.allowedValues ?: "none"}; ${it.description}") }
                    Text("Return: ${binding.descriptor.returnType}; enum=${binding.descriptor.returnAllowedValues ?: "none"}; ${binding.descriptor.returnDescription}")
                }
            }
        }
    }
}
