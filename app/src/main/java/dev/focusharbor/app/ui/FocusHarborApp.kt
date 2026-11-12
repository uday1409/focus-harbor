package dev.focusharbor.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.focusharbor.app.FocusUiState
import dev.focusharbor.app.FocusViewModel
import dev.focusharbor.app.R
import dev.focusharbor.app.apps.LaunchableApp
import dev.focusharbor.app.focus.ExitText
import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.FocusStore
import dev.focusharbor.app.focus.FocusTime
import dev.focusharbor.app.focus.remainingMillis
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun FocusHarborApp(model: FocusViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExit by rememberSaveable { mutableStateOf(false) }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showLauncherPicker by rememberSaveable { mutableStateOf(false) }
    val active = state.session?.isActive == true
    val requestExit: () -> Unit = {
        if (!state.busy) {
            if (state.session?.requireExitText == true) {
                showExit = true
            } else {
                model.endFocus("")
            }
        }
    }
    val homeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { model.refresh() }
    val launchHomeSelection: (Intent) -> Unit = { intent ->
        try {
            homeLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            model.notify(R.string.home_open_error)
        } catch (_: SecurityException) {
            model.notify(R.string.home_open_error)
        }
    }
    val chooseHome: () -> Unit = { launchHomeSelection(model.homeSelectionIntent()) }
    val closeLauncherPicker: () -> Unit = {
        showLauncherPicker = false
        model.dismissLauncherRecovery()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.getString(it))
            model.consumeMessage(it)
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            showExit = false
        } else {
            showPicker = false
            showLauncherPicker = false
        }
    }
    BackHandler(enabled = active) {
        if (showExit) showExit = false else requestExit()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomBar(
                state = state,
                onStart = model::startFocus,
                onExit = requestExit,
                onPhone = model::openPhone,
                onRecovery = { showRecovery = true },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.storageError -> ErrorPanel(
                    message = stringResource(R.string.storage_error),
                    onRetry = model::retryStorage,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.session == null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading))
                }
                active -> FocusScreen(
                    state, model::launchApp, model::refresh, requestExit, model::keepGoing, model::currentTime,
                )
                else -> SetupScreen(
                    state = state,
                    onChooseHome = chooseHome,
                    onRequireExitText = model::setRequireExitText,
                    onChooseApps = { showPicker = true },
                    onClearSelection = model::clearSelection,
                    onSafetyAccepted = model::setSafetyAccepted,
                    onPresetSelect = model::selectPreset,
                    onPresetCreate = model::createPreset,
                    onPresetRename = model::renamePreset,
                    onPresetDelete = model::deletePreset,
                    onIntention = model::setIntention,
                    onDuration = model::setDuration,
                    onChooseNormalLauncher = { showLauncherPicker = true; model.refreshLaunchers() },
                    onOpenNormalLauncher = model::requestNormalHome,
                )
            }
        }
    }

    if (showExit && active && state.session?.requireExitText == true) {
        ExitDialog(
            busy = state.busy,
            onDismiss = { showExit = false },
            onConfirm = model::endFocus,
        )
    }
    if (showRecovery) {
        AlertDialog(
            onDismissRequest = { showRecovery = false },
            title = { Text(stringResource(R.string.recovery_title)) },
            text = {
                Text(
                    stringResource(R.string.recovery_body),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showRecovery = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (showPicker && !active && state.session != null && !state.storageError) {
        AppPicker(
            state = state,
            onDismiss = { showPicker = false; model.saveAppSelection() },
            onQuery = model::setQuery,
            onToggle = model::toggleApp,
            onOpen = model::launchApp,
            onRetry = model::refresh,
        )
    }
    if ((showLauncherPicker || state.launcherRecovery) && !active &&
        state.session != null && !state.storageError
    ) {
        NormalLauncherDialog(
            state = state,
            onSelect = {
                showLauncherPicker = false
                model.selectNormalLauncher(it, openAfterSave = state.launcherRecovery)
            },
            onDismiss = closeLauncherPicker,
            onRetry = model::refreshLaunchers,
            onHomeSettings = {
                closeLauncherPicker()
                launchHomeSelection(Intent(Settings.ACTION_HOME_SETTINGS))
            },
            onRecovery = { closeLauncherPicker(); showRecovery = true },
        )
    }
}

@Composable
private fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(R.drawable.ic_harbor),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Column {
            Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupScreen(
    state: FocusUiState,
    onChooseHome: () -> Unit,
    onRequireExitText: (Boolean) -> Unit,
    onChooseApps: () -> Unit,
    onClearSelection: () -> Unit,
    onSafetyAccepted: (Boolean) -> Unit,
    onPresetSelect: (String) -> Unit,
    onPresetCreate: (String) -> Unit,
    onPresetRename: (String) -> Unit,
    onPresetDelete: () -> Unit,
    onIntention: (String) -> Unit,
    onDuration: (Int?) -> Unit,
    onChooseNormalLauncher: () -> Unit,
    onOpenNormalLauncher: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.testTag("setup-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Brand() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.setup_heading),
                    style = MaterialTheme.typography.displaySmall,
                )
                SupportingText(stringResource(R.string.setup_description))
            }
        }
        item {
            PresetSection(state, onPresetSelect, onPresetCreate, onPresetRename, onPresetDelete)
        }
        item { NormalLauncherSection(state, onChooseNormalLauncher, onOpenNormalLauncher) }
        item {
            SectionCard {
                Eyebrow(stringResource(R.string.step_home))
                Text(
                    stringResource(
                        if (state.defaultHome) R.string.home_ready else R.string.home_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SupportingText(stringResource(R.string.home_description))
                OutlinedButton(onClick = onChooseHome, enabled = !state.busy) {
                    Text(
                        stringResource(
                            if (state.defaultHome) R.string.home_settings else R.string.set_home,
                        ),
                    )
                }
            }
        }
        item { SessionOptions(state, onIntention, onDuration) }
        item {
            SectionCard {
                Eyebrow(stringResource(R.string.step_exit))
                Text(
                    stringResource(R.string.exit_option_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SupportingText(stringResource(R.string.exit_option_description))
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("require-exit-text")
                        .toggleable(
                            value = state.draftRequireExitText,
                            enabled = !state.busy,
                            role = Role.Checkbox,
                            onValueChange = onRequireExitText,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.draftRequireExitText,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(stringResource(R.string.require_exit_text))
                }
            }
        }
        item {
            SectionCard {
                Eyebrow(stringResource(R.string.step_apps))
                Text(
                    stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SupportingText(stringResource(R.string.apps_description))
                Text(
                    pluralStringResource(
                        R.plurals.selected_count, state.draftAllowed.size, state.draftAllowed.size,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                val selected = state.apps.filter { it.packageName in state.draftAllowed }
                if (selected.isNotEmpty()) {
                    SupportingText(selected.joinToString { it.label })
                }
                if (state.catalogLoaded && selected.size < state.draftAllowed.size) {
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
                    OutlinedButton(onClick = onChooseApps, enabled = !state.busy) {
                        Text(stringResource(R.string.choose_apps))
                    }
                    if (state.draftAllowed.isNotEmpty()) {
                        TextButton(onClick = onClearSelection, enabled = !state.busy) {
                            Text(stringResource(R.string.clear_selection))
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .testTag("safety-ack")
                    .toggleable(
                        value = state.safetyAccepted,
                        enabled = !state.busy,
                        role = Role.Checkbox,
                        onValueChange = onSafetyAccepted,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = state.safetyAccepted,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    stringResource(R.string.safety_acknowledgment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SupportingText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BottomBar(
    state: FocusUiState,
    onStart: () -> Unit,
    onExit: () -> Unit,
    onPhone: () -> Unit,
    onRecovery: () -> Unit,
) {
    val active = state.session?.isActive == true
    val ready = state.session != null && !state.storageError
    val canStart = ready && !state.busy && state.defaultHome && state.catalogLoaded &&
        !state.catalogError && state.safetyAccepted &&
        state.launcherCatalogLoaded && !state.launcherCatalogError &&
        state.normalLauncher != null && state.normalLaunchers.any { it.target == state.normalLauncher } &&
        state.selectedPresetId != null &&
        state.draftIntention.trim().length <= FocusStore.MAX_INTENTION_LENGTH &&
        FocusStore.validDuration(state.draftDurationMinutes) &&
        state.draftAllowed.isNotEmpty() &&
        state.apps.map { it.packageName }.containsAll(state.draftAllowed)
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (ready) {
                Button(
                    onClick = if (active) onExit else onStart,
                    enabled = if (active) !state.busy else canStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .testTag(if (active) "leave-focus" else "start-focus"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(stringResource(if (active) R.string.leave_focus else R.string.start_focus))
                }
                if (!active && !canStart) {
                    Text(
                        stringResource(R.string.start_requirements),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPhone) { Text(stringResource(R.string.phone)) }
                TextButton(onClick = onRecovery) { Text(stringResource(R.string.recovery)) }
            }
        }
    }
}

@Composable
private fun AppPicker(
    state: FocusUiState,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (LaunchableApp) -> Unit,
    onRetry: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Surface {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(24.dp),
                    ) { Text(stringResource(R.string.done)) }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SupportingText(
                    pluralStringResource(
                        R.plurals.selected_count, state.draftAllowed.size, state.draftAllowed.size,
                    ),
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    label = { Text(stringResource(R.string.search_apps)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                when {
                    state.catalogError -> ErrorPanel(
                        message = stringResource(R.string.catalog_error),
                        onRetry = onRetry,
                    )
                    !state.catalogLoaded -> CircularProgressIndicator()
                    else -> {
                        val apps = state.apps.filter {
                            it.label.contains(state.query, ignoreCase = true) ||
                                it.packageName.contains(state.query, ignoreCase = true)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f).testTag("app-picker-list"),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (apps.isEmpty()) {
                                item {
                                    SupportingText(
                                        stringResource(
                                            if (state.apps.isEmpty()) {
                                                R.string.no_apps
                                            } else {
                                                R.string.no_search_results
                                            },
                                        ),
                                    )
                                }
                            }
                            items(apps, key = { it.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    selected = app.packageName in state.draftAllowed,
                                    onToggle = { onToggle(app.packageName) },
                                    onOpen = { onOpen(app) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LaunchableApp,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val openDescription = stringResource(R.string.open_app, app.label)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .testTag("pick-${app.packageName}")
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            app.icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = onOpen,
            modifier = Modifier.semantics { contentDescription = openDescription },
        ) { Text(stringResource(R.string.open_short)) }
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

@Composable
private fun FocusScreen(
    state: FocusUiState,
    onOpen: (LaunchableApp) -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    onKeepGoing: (Int?) -> Unit,
    currentTime: () -> FocusTime,
) {
    val session = state.session ?: return
    val allowed = state.apps.filter { it.packageName in session.allowedPackages }
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(currentTime()) }
    var extending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = currentTime()
                delay(1_000)
            }
        }
    }
    val remaining = session.remainingMillis(now)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.testTag("focus-apps"),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { FocusHeader(session, now, remaining) }
        if (remaining == 0L) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard {
                    Text(
                        stringResource(R.string.time_reached_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("time-reached"),
                    )
                    SupportingText(stringResource(R.string.time_reached_description))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onFinish,
                            enabled = !state.busy,
                            modifier = Modifier.testTag("time-reached-finish"),
                        ) { Text(stringResource(R.string.finish)) }
                        OutlinedButton(
                            onClick = { extending = true },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("keep-going"),
                        ) { Text(stringResource(R.string.keep_going)) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Eyebrow(stringResource(R.string.focus_apps))
            }
        }
        if (state.catalogError) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorPanel(stringResource(R.string.catalog_error), onRetry)
            }
        } else if (!state.catalogLoaded) {
            item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator() }
        } else {
            if (allowed.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SupportingText(stringResource(R.string.focus_no_apps))
                }
            } else if (allowed.size < session.allowedPackages.size) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SupportingText(stringResource(R.string.missing_apps))
                }
            }
            items(allowed, key = { it.packageName }) { app ->
                OutlinedCard(
                    onClick = { onOpen(app) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp)
                        .testTag("allowed-${app.packageName}"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            app.label,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    if (extending) {
        DurationDialog(
            title = stringResource(R.string.keep_going_title),
            initial = 25,
            onDismiss = { extending = false },
            onSelect = { extending = false; onKeepGoing(it) },
        )
    }
}

@Composable
private fun FocusHeader(session: FocusSession, now: FocusTime, remaining: Long?) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Brand()
        Spacer(Modifier.height(6.dp))
        Eyebrow(stringResource(R.string.focus_status))
        session.presetName?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("session-preset"))
        }
        if (session.intention.isNotBlank()) {
            Eyebrow(stringResource(R.string.your_intention))
            Text(
                session.intention,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().testTag("session-intention"),
            )
        } else {
            Text(stringResource(R.string.focus_heading), style = MaterialTheme.typography.displaySmall)
        }
        SupportingText(stringResource(R.string.focus_description))
        Box(
            modifier = Modifier.fillMaxWidth().height(192.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(184.dp)) {
                drawCircle(fill, radius = size.minDimension * 0.41f)
                drawCircle(ring, style = Stroke(width = 1.dp.toPx()))
                drawCircle(
                    ring.copy(alpha = 0.5f),
                    radius = size.minDimension * 0.47f,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeText(remaining ?: (now.epochMillis - session.startedAtMillis).coerceAtLeast(0))
                Text(
                    stringResource(if (remaining == null) R.string.focus_elapsed else R.string.time_remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimeText(milliseconds: Long) {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    Text(
        String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60),
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun ExitDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    val words = ExitText.wordCount(entered)
    val valid = words >= ExitText.MIN_WORDS
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.exit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SupportingText(stringResource(R.string.exit_description))
                OutlinedTextField(
                    value = entered,
                    onValueChange = { entered = it },
                    modifier = Modifier.fillMaxWidth().testTag("exit-input"),
                    enabled = !busy,
                    label = { Text(stringResource(R.string.exit_label)) },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = ExitKeyboard,
                    supportingText = {
                        Text(
                            pluralStringResource(
                                R.plurals.exit_word_count, words, words, ExitText.MIN_WORDS,
                            ),
                        )
                    },
                )
                SupportingText(
                    stringResource(if (valid) R.string.exit_ready else R.string.exit_helper),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(entered) },
                enabled = valid && !busy,
                modifier = Modifier.testTag("confirm-exit"),
            ) { Text(stringResource(R.string.end_focus)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.stay_focused))
            }
        },
    )
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

private val ExitKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    autoCorrectEnabled = true,
)
