package dev.focusharbor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.focusharbor.app.FocusUiState
import dev.focusharbor.app.R
import dev.focusharbor.app.home.LauncherTarget

@Composable
internal fun NormalLauncherSection(
    state: FocusUiState,
    onChoose: () -> Unit,
    onOpen: () -> Unit,
) {
    val selected = state.normalLaunchers.firstOrNull { it.target == state.normalLauncher }
    SectionCard {
        Eyebrow(stringResource(R.string.step_normal_home))
        Text(stringResource(R.string.normal_launcher_title), style = MaterialTheme.typography.headlineSmall)
        SupportingText(stringResource(R.string.normal_launcher_description))
        if (state.normalLauncher != null) {
            Text(
                selected?.label ?: state.normalLauncher.packageName,
                modifier = Modifier.testTag("selected-normal-launcher"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val error = state.launcherError ?: if (
            state.normalLauncher != null && state.launcherCatalogLoaded && selected == null
        ) R.string.normal_launcher_unavailable else null
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = onChoose,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("choose-normal-launcher"),
        ) {
            Text(
                stringResource(
                    if (state.normalLauncher == null) R.string.choose_normal_launcher else R.string.change_normal_launcher,
                ),
            )
        }
        if (selected != null && !state.launcherCatalogError) {
            TextButton(
                onClick = onOpen,
                enabled = !state.busy,
                modifier = Modifier.testTag("open-normal-launcher"),
            ) { Text(stringResource(R.string.open_normal_launcher)) }
        }
    }
}

@Composable
internal fun NormalLauncherDialog(
    state: FocusUiState,
    onSelect: (LauncherTarget) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onHomeSettings: () -> Unit,
    onRecovery: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("normal-launcher-dialog"),
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.choose_normal_launcher)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.launcherRecovery) {
                    SupportingText(stringResource(R.string.launcher_recovery_description))
                }
                state.launcherError?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                when {
                    state.launcherCatalogError -> {
                        TextButton(onClick = onRetry, enabled = !state.busy) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    !state.launcherCatalogLoaded -> CircularProgressIndicator()
                    state.normalLaunchers.isEmpty() -> {
                        SupportingText(stringResource(R.string.no_normal_launchers))
                        TextButton(onClick = onRetry, enabled = !state.busy) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    else -> state.normalLaunchers.forEach { launcher ->
                        OutlinedButton(
                            onClick = { onSelect(launcher.target) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("normal-launcher-${launcher.target.packageName}/${launcher.target.className}"),
                        ) {
                            Column {
                                Text(launcher.label)
                                Text(launcher.target.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onHomeSettings,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("launcher-home-settings"),
                ) {
                    Text(stringResource(R.string.home_settings))
                }
                TextButton(
                    onClick = onRecovery,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("launcher-recovery-help"),
                ) {
                    Text(stringResource(R.string.recovery))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.busy,
                modifier = Modifier.testTag("launcher-stay-in-setup"),
            ) { Text(stringResource(R.string.stay_in_setup)) }
        },
    )
}
