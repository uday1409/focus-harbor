package dev.focusharbor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.focusharbor.app.FocusUiState
import dev.focusharbor.app.R
import dev.focusharbor.app.focus.FocusStore

@Composable
internal fun SessionOptions(
    state: FocusUiState,
    onIntention: (String) -> Unit,
    onDuration: (Int?) -> Unit,
) {
    var choosingDuration by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Eyebrow(stringResource(R.string.step_session))
        Text(stringResource(R.string.session_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.draftIntention,
            onValueChange = onIntention,
            modifier = Modifier.fillMaxWidth().testTag("intention-input"),
            label = { Text(stringResource(R.string.intention_label)) },
            enabled = !state.busy,
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            isError = state.draftIntention.trim().length > FocusStore.MAX_INTENTION_LENGTH,
            supportingText = {
                Text(
                    stringResource(
                        R.string.intention_length, state.draftIntention.trim().length, FocusStore.MAX_INTENTION_LENGTH,
                    ),
                )
            },
        )
        SupportingText(stringResource(R.string.intention_privacy))
        OutlinedButton(
            onClick = { choosingDuration = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("duration-selector"),
        ) { Text(durationLabel(state.draftDurationMinutes)) }
        SupportingText(stringResource(R.string.duration_description))
    }
    if (choosingDuration) {
        DurationDialog(
            title = stringResource(R.string.duration_title),
            initial = state.draftDurationMinutes,
            onDismiss = { choosingDuration = false },
            onSelect = { choosingDuration = false; onDuration(it) },
        )
    }
}

@Composable
internal fun durationLabel(minutes: Int?): String =
    minutes?.let { pluralStringResource(R.plurals.duration_minutes, it, it) }
        ?: stringResource(R.string.open_ended)

@Composable
internal fun DurationDialog(
    title: String,
    initial: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val quickChoices = listOf(null, 15, 25, 45, 60)
    var selected by rememberSaveable { mutableStateOf(initial) }
    var custom by rememberSaveable { mutableStateOf(initial !in quickChoices) }
    var customText by rememberSaveable { mutableStateOf(if (initial !in quickChoices) initial.toString() else "") }
    val value = if (custom) customText.toIntOrNull() else selected
    val valid = (!custom || value != null) && FocusStore.validDuration(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                quickChoices.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("duration-choice-${minutes ?: "open"}")
                            .selectable(
                                selected = !custom && selected == minutes,
                                role = Role.RadioButton,
                                onClick = { custom = false; selected = minutes },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !custom && selected == minutes, onClick = null)
                        Text(durationLabel(minutes))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("duration-custom")
                        .selectable(
                            selected = custom,
                            role = Role.RadioButton,
                            onClick = { custom = true },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = custom, onClick = null)
                    Text(stringResource(R.string.custom_duration))
                }
                if (custom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        modifier = Modifier.fillMaxWidth().testTag("duration-minutes-input"),
                        label = { Text(stringResource(R.string.minutes_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = customText.isNotEmpty() && !valid,
                        supportingText = { Text(stringResource(R.string.invalid_duration)) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(value) },
                enabled = valid,
                modifier = Modifier.testTag("duration-save"),
            ) { Text(stringResource(R.string.apply_duration)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
