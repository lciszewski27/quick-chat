package dev.lciszewski27.quickchat.ui.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lciszewski27.quickchat.domain.model.SecretDecl
import dev.lciszewski27.quickchat.domain.model.Skill
import dev.lciszewski27.quickchat.ui.settings.SettingsUiEvent
import dev.lciszewski27.quickchat.ui.settings.SettingsUiState

/**
 * User skills: JavaScript tools the assistant created (or the user wrote)
 * via the create_skill tool. A skill reaches the model only after passing
 * the test gate here. Secret values are set here and injected at runtime —
 * the model only ever sees secret names.
 */
@Composable
internal fun SkillsSettingsPage(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Skills",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { onEvent(SettingsUiEvent.ShowNewSkill) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New")
            }
        }
        Text(
            "Ask the assistant in chat (“make a skill that …”) or write one below. " +
                "Skills stay hidden from the model until they pass a test run here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.skills.isEmpty()) {
            Text(
                "No skills yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.skills.forEach { skill ->
                SkillCard(
                    skill = skill,
                    onEvent = onEvent
                )
            }
        }
    }

    if (uiState.showNewSkill) {
        SkillEditorDialog(
            skill = null,
            onDismiss = { onEvent(SettingsUiEvent.DismissSkillEditor) },
            onSave = { name, desc, params, code ->
                onEvent(SettingsUiEvent.SaveSkill(null, name, desc, params, code))
            }
        )
    }

    uiState.editingSkillId?.let { id ->
        uiState.skills.find { it.id == id }?.let { skill ->
            SkillEditorDialog(
                skill = skill,
                onDismiss = { onEvent(SettingsUiEvent.DismissSkillEditor) },
                onSave = { name, desc, params, code ->
                    onEvent(SettingsUiEvent.SaveSkill(id, name, desc, params, code))
                }
            )
        }
    }

    uiState.testingSkillId?.let { id ->
        uiState.skills.find { it.id == id }?.let { skill ->
            SkillTestDialog(
                skill = skill,
                args = uiState.testArgs,
                running = uiState.testRunning,
                result = uiState.testResult,
                onArgs = { onEvent(SettingsUiEvent.SetSkillTestArgs(it)) },
                onRun = { onEvent(SettingsUiEvent.RunSkillTest) },
                onDismiss = { onEvent(SettingsUiEvent.DismissSkillTest) }
            )
        }
    }

    uiState.secretsSkillId?.let { id ->
        uiState.skills.find { it.id == id }?.let { skill ->
            SkillSecretsDialog(
                skill = skill,
                initialValues = uiState.secretValues,
                onDismiss = { onEvent(SettingsUiEvent.DismissSkillSecrets) },
                onSave = { decls, values ->
                    onEvent(SettingsUiEvent.SaveSkillSecrets(id, decls, values))
                }
            )
        }
    }

    uiState.confirmDeleteSkillId?.let { id ->
        val name = uiState.skills.find { it.id == id }?.name ?: "this skill"
        AlertDialog(
            onDismissRequest = { onEvent(SettingsUiEvent.DismissDeleteSkill) },
            title = { Text("Delete skill?") },
            text = { Text("“$name” and its secrets will be removed. Chats that used it keep their history.") },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsUiEvent.ConfirmDeleteSkill) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(SettingsUiEvent.DismissDeleteSkill) }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    onEvent: (SettingsUiEvent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        skill.name.ifBlank { "Untitled skill" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusLine(skill),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!skill.tested) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.ToggleSkillEnabled(skill.id, it)) }
                )
            }
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "tool: ${skill.toolName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onEvent(SettingsUiEvent.ShowSkillTest(skill.id)) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Test skill")
                }
                IconButton(onClick = { onEvent(SettingsUiEvent.ShowSkillSecrets(skill.id)) }) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = "Secrets",
                        tint = if (skill.secrets.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onEvent(SettingsUiEvent.ShowEditSkill(skill.id)) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit skill")
                }
                IconButton(onClick = { onEvent(SettingsUiEvent.RequestDeleteSkill(skill.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete skill")
                }
            }
        }
    }
}

private fun statusLine(skill: Skill): String = when {
    !skill.tested -> "Draft — test to activate"
    skill.enabled -> "Active" + if (skill.secrets.isNotEmpty()) " • ${skill.secrets.size} secret(s)" else ""
    else -> "Tested • off"
}

@Composable
private fun SkillEditorDialog(
    skill: Skill?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, paramsSchema: String, code: String) -> Unit
) {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var params by remember(skill?.id) { mutableStateOf(skill?.paramsSchema ?: "") }
    var code by remember(skill?.id) { mutableStateOf(skill?.code.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skill == null) "New skill" else "Edit skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (when should the model use it?)") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = params,
                    onValueChange = { params = it },
                    label = { Text("Arguments JSON Schema (optional)") },
                    placeholder = { Text("{\"type\":\"object\"}") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("JavaScript code") },
                    placeholder = { Text("return args.x * 2;") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
                if (skill != null) {
                    Text(
                        "Saving new code resets this skill to draft — re-test to reactivate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, params, code) },
                enabled = name.isNotBlank() && code.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun SkillTestDialog(
    skill: Skill,
    args: String,
    running: Boolean,
    result: String?,
    onArgs: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test “${skill.name}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Runs with real stored secrets. A pass marks the skill tested and active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = args,
                    onValueChange = onArgs,
                    label = { Text("Test arguments (JSON object)") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run test")
                }
                result?.let {
                    val passed = !it.startsWith("Error")
                    if (passed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Passed — skill is now active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = it.take(4000),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = null,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun SkillSecretsDialog(
    skill: Skill,
    initialValues: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (decls: List<SecretDecl>, values: Map<String, String>) -> Unit
) {
    var decls by remember(skill.id) { mutableStateOf(skill.secrets) }
    var values by remember(skill.id) { mutableStateOf(initialValues) }
    var showValues by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    val nameOk = newName.matches(Regex("^[A-Z0-9_]{1,64}$")) && decls.none { it.name == newName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secrets for “${skill.name}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Values are stored on this device and injected at runtime as secrets.NAME. " +
                        "The model only sees the names.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                decls.forEach { decl ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    decl.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                if (decl.description.isNotBlank()) {
                                    Text(
                                        decl.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = {
                                decls = decls.filterNot { it.name == decl.name }
                                values = values - decl.name
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove ${decl.name}")
                            }
                        }
                        OutlinedTextField(
                            value = values[decl.name].orEmpty(),
                            onValueChange = { values = values + (decl.name to it) },
                            label = { Text("Value (only on this device)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (showValues) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showValues = !showValues }) {
                                    Icon(
                                        if (showValues) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showValues) "Hide values" else "Show values"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text("Add secret", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.trim().uppercase() },
                    label = { Text("NAME (A-Z, 0-9, _)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    onClick = {
                        decls = decls + SecretDecl(newName, newDesc)
                        newName = ""
                        newDesc = ""
                    },
                    enabled = nameOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(decls, values.filterValues { it.isNotBlank() }) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}
