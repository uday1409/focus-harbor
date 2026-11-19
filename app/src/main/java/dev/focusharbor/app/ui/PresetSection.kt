package dev.focusharbor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.focusharbor.app.FocusUiState
import dev.focusharbor.app.R
import dev.focusharbor.app.focus.FocusStore

@Composable
internal fun PresetSection(
    state: FocusUiState,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onChooseApps: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val selected = state.presets.firstOrNull { it.id == state.selectedPresetId }
    var menu by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Eyebrow(stringResource(R.string.step_apps))
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall)
        SupportingText(stringResource(R.string.apps_description))
        Box {
            OutlinedButton(
                onClick = { menu = true },
                enabled = !state.busy && selected != null,
                modifier = Modifier.fillMaxWidth().testTag("preset-selector"),
            ) {
                Text(selected?.name.orEmpty())
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                state.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                            menu = false
                            onSelect(preset.id)
                        },
                        modifier = Modifier.testTag("preset-option-${preset.id}"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { name = ""; editing = "new" },
                enabled = !state.busy,
                modifier = Modifier.testTag("preset-new"),
            ) { Text(stringResource(R.string.new_preset)) }
            TextButton(
                onClick = { name = selected?.name.orEmpty(); editing = "rename" },
                enabled = !state.busy && selected != null,
                modifier = Modifier.testTag("preset-rename"),
            ) { Text(stringResource(R.string.rename_preset)) }
            TextButton(
                onClick = { deleting = true },
                enabled = !state.busy && selected != null && state.presets.size > 1,
                modifier = Modifier.testTag("preset-delete"),
            ) { Text(stringResource(R.string.delete_preset)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            pluralStringResource(
                R.plurals.selected_count, state.draftAllowed.size, state.draftAllowed.size,
            ),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        val selectedApps = state.apps.filter { it.packageName in state.draftAllowed }
        if (selectedApps.isNotEmpty()) {
            SupportingText(selectedApps.joinToString { it.label })
        }
        if (state.catalogLoaded && selectedApps.size < state.draftAllowed.size) {
            Text(
                stringResource(R.string.missing_apps),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onChooseApps,
                enabled = !state.busy,
                modifier = Modifier.testTag("choose-apps"),
            ) {
                Text(stringResource(R.string.choose_apps))
            }
            if (state.draftAllowed.isNotEmpty()) {
                TextButton(onClick = onClearSelection, enabled = !state.busy) {
                    Text(stringResource(R.string.clear_selection))
                }
            }
        }
    }
    if (editing != null) {
        val duplicate = state.presets.any {
            (editing == "new" || it.id != selected?.id) && it.name.equals(name.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(stringResource(if (editing == "new") R.string.new_preset else R.string.rename_preset))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (editing == "new") SupportingText(stringResource(R.string.new_preset_description))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.preset_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("preset-name-input"),
                        isError = duplicate || (name.isNotEmpty() && !FocusStore.validPresetName(name)),
                        supportingText = {
                            Text(
                                stringResource(
                                    if (duplicate) R.string.duplicate_preset_name else R.string.invalid_preset_name,
                                ),
                            )
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editing == "new") onCreate(name.trim()) else onRename(name.trim())
                        editing = null
                    },
                    enabled = FocusStore.validPresetName(name) && !duplicate,
                    modifier = Modifier.testTag("preset-save"),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_preset)) },
            text = { Text(stringResource(R.string.delete_preset_description, selected?.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = { deleting = false; onDelete() },
                    modifier = Modifier.testTag("preset-delete-confirm"),
                ) { Text(stringResource(R.string.delete_preset)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
