package dev.focusharbor.app

import android.app.Application
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.focusharbor.app.apps.AppCatalog
import dev.focusharbor.app.apps.LaunchableApp
import dev.focusharbor.app.focus.FocusSession
import dev.focusharbor.app.focus.FocusStore
import dev.focusharbor.app.focus.AndroidFocusClock
import dev.focusharbor.app.focus.FocusPlan
import dev.focusharbor.app.focus.FocusPreset
import dev.focusharbor.app.focus.SessionMutation
import dev.focusharbor.app.focus.focusDataStore
import dev.focusharbor.app.home.HomeHandoffGuard
import dev.focusharbor.app.home.HomeRequest
import dev.focusharbor.app.home.LauncherTarget
import dev.focusharbor.app.home.NormalLauncher
import dev.focusharbor.app.home.NormalLauncherCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusUiState(
    val session: FocusSession? = null,
    val apps: List<LaunchableApp> = emptyList(),
    val catalogLoaded: Boolean = false,
    val catalogError: Boolean = false,
    val storageError: Boolean = false,
    val defaultHome: Boolean = false,
    val normalLauncher: LauncherTarget? = null,
    val normalLaunchers: List<NormalLauncher> = emptyList(),
    val launcherCatalogLoaded: Boolean = false,
    val launcherCatalogError: Boolean = false,
    val launcherRecovery: Boolean = false,
    @param:StringRes val launcherError: Int? = null,
    val draftAllowed: Set<String> = emptySet(),
    val draftRequireExitText: Boolean = false,
    val presets: List<FocusPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val draftIntention: String = "",
    val draftDurationMinutes: Int? = null,
    val safetyAccepted: Boolean = false,
    val query: String = "",
    val busy: Boolean = false,
    @param:StringRes val message: Int? = null,
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FocusStore(application.focusDataStore)
    private val catalog = AppCatalog(application)
    private val launcherCatalog = NormalLauncherCatalog(application)
    private val clock = AndroidFocusClock(application)
    private val handoffGuard = HomeHandoffGuard()
    private val mutableState = MutableStateFlow(FocusUiState())
    val state = mutableState.asStateFlow()
    private val mutableHomeRequests = MutableStateFlow<HomeRequest?>(null)
    val homeRequests = mutableHomeRequests.asStateFlow()
    private var nextHomeRequestId = 0L
    private var automaticHandoffBlocked = false
    private var storageJob: Job? = null
    private var catalogJob: Job? = null
    private var launcherJob: Job? = null

    init {
        retryStorage()
    }

    fun retryStorage() {
        storageJob?.cancel()
        mutableState.update { it.copy(storageError = false) }
        storageJob = viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                store.initializePresets(
                    listOf(
                        FocusPreset("work", context.getString(R.string.preset_work)),
                        FocusPreset("study", context.getString(R.string.preset_study)),
                        FocusPreset("sleep", context.getString(R.string.preset_sleep)),
                        FocusPreset("family", context.getString(R.string.preset_family)),
                    ),
                )
                store.states.collect { saved ->
                    val session = saved.session
                    mutableState.update {
                        val initializeDraft = it.session == null ||
                            (it.session.isActive && !session.isActive)
                        val resetApps = initializeDraft || it.selectedPresetId != saved.selectedPresetId
                        val selected = saved.presets.firstOrNull { preset -> preset.id == saved.selectedPresetId }
                        it.copy(
                            session = session,
                            normalLauncher = saved.normalLauncher,
                            presets = saved.presets,
                            selectedPresetId = saved.selectedPresetId,
                            storageError = false,
                            draftAllowed = if (resetApps) {
                                selected?.allowedPackages ?: session.allowedPackages
                            } else {
                                it.draftAllowed
                            },
                            draftRequireExitText = if (initializeDraft) {
                                session.requireExitText
                            } else {
                                it.draftRequireExitText
                            },
                            draftIntention = if (initializeDraft) "" else it.draftIntention,
                            draftDurationMinutes = if (initializeDraft) saved.lastDurationMinutes else it.draftDurationMinutes,
                            safetyAccepted = if (initializeDraft) false else it.safetyAccepted,
                        )
                    }
                }
            } catch (error: IOException) {
                Log.e(TAG, "Unable to read the saved focus session.", error)
                mutableState.update { it.copy(storageError = true) }
            }
        }
    }

    fun currentTime() = clock.now()

    fun setIntention(text: String) {
        if (canEdit()) mutableState.update { it.copy(draftIntention = text) }
    }

    fun setDuration(minutes: Int?) {
        if (!canEdit()) return
        if (FocusStore.validDuration(minutes)) {
            mutableState.update { it.copy(draftDurationMinutes = minutes) }
        } else {
            notify(R.string.invalid_duration)
        }
    }

    fun selectPreset(id: String) {
        if (canEdit()) mutate { store.selectPreset(id, state.value.draftAllowed) }
    }

    fun createPreset(name: String) {
        if (canEdit()) mutate { store.createPreset(name, state.value.draftAllowed) }
    }

    fun renamePreset(name: String) {
        val id = state.value.selectedPresetId
        if (canEdit() && id != null) mutate { store.renamePreset(id, name) }
    }

    fun deletePreset() {
        val id = state.value.selectedPresetId
        if (canEdit() && id != null) mutate { store.deletePreset(id) }
    }

    fun saveAppSelection() {
        val id = state.value.selectedPresetId
        if (canEdit() && id != null) mutate { store.saveSelection(id, state.value.draftAllowed) }
    }

    fun keepGoing(minutes: Int?) {
        if (!state.value.busy) mutate { store.keepGoing(minutes, clock.now()) }
    }

    fun refresh() {
        mutableState.update { it.copy(defaultHome = isDefaultHome()) }
        refreshLaunchers()
        if (catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { catalog.load() }
                mutableState.update {
                    it.copy(apps = apps, catalogLoaded = true, catalogError = false)
                }
            } catch (error: SecurityException) {
                catalogFailed(error)
            } catch (error: Resources.NotFoundException) {
                catalogFailed(error)
            }
        }
    }

    private fun catalogFailed(error: RuntimeException) {
        Log.e(TAG, "Unable to load launchable apps in the current Android profile.", error)
        mutableState.update { it.copy(catalogError = true, catalogLoaded = true) }
    }

    fun refreshLaunchers() {
        if (launcherJob?.isActive == true) return
        launcherJob = viewModelScope.launch {
            try {
                val launchers = withContext(Dispatchers.IO) { launcherCatalog.load() }
                mutableState.update {
                    it.copy(normalLaunchers = launchers, launcherCatalogLoaded = true, launcherCatalogError = false)
                }
            } catch (error: SecurityException) {
                launcherCatalogFailed(error)
            } catch (error: Resources.NotFoundException) {
                launcherCatalogFailed(error)
            }
        }
    }

    private fun launcherCatalogFailed(error: RuntimeException) {
        Log.e(TAG, "Unable to discover normal Home launchers in the current Android profile.", error)
        mutableState.update {
            it.copy(launcherCatalogLoaded = true, launcherCatalogError = true, launcherError = R.string.launcher_catalog_error)
        }
    }

    fun selectNormalLauncher(target: LauncherTarget, openAfterSave: Boolean) {
        if (!canEdit()) return
        if (state.value.launcherCatalogError || state.value.normalLaunchers.none { it.target == target }) {
            showLauncherRecovery(R.string.normal_launcher_unavailable)
            refreshLaunchers()
            return
        }
        mutate(onSaved = {
            automaticHandoffBlocked = false
            mutableState.update { it.copy(launcherRecovery = false, launcherError = null) }
            if (openAfterSave) requestNormalHome()
        }) { store.saveNormalLauncher(target) }
    }

    fun acceptEntryIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) &&
            !intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        ) {
            enqueueHomeRequest(automatic = true)
        } else {
            mutableHomeRequests.value = null
            handoffGuard.reset()
            mutableState.update { it.copy(launcherRecovery = false) }
        }
    }

    fun requestNormalHome() {
        automaticHandoffBlocked = false
        enqueueHomeRequest(automatic = false)
    }

    private fun enqueueHomeRequest(automatic: Boolean) {
        mutableHomeRequests.value = HomeRequest(++nextHomeRequestId, automatic)
    }

    suspend fun resolveHomeRequest(request: HomeRequest): LauncherTarget? {
        if (mutableHomeRequests.value != request) return null
        try {
            if (store.states.first().session.isActive || state.value.busy) return null
            if (request.automatic && automaticHandoffBlocked) {
                showLauncherRecovery(state.value.launcherError ?: R.string.normal_launcher_open_error)
                return null
            }
            val launchers = withContext(Dispatchers.IO) { launcherCatalog.load() }
            val saved = store.states.first()
            if (saved.session.isActive || state.value.busy) return null
            mutableState.update {
                it.copy(normalLaunchers = launchers, launcherCatalogLoaded = true, launcherCatalogError = false)
            }
            val target = saved.normalLauncher
            return when {
                target == null -> {
                    showLauncherRecovery(R.string.normal_launcher_required)
                    null
                }
                launchers.none { it.target == target } -> {
                    showLauncherRecovery(R.string.normal_launcher_unavailable)
                    null
                }
                else -> target
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to verify the saved session before returning to normal Home.", error)
            mutableState.update { it.copy(storageError = true) }
        } catch (error: SecurityException) {
            launcherCatalogFailed(error)
            showLauncherRecovery(R.string.launcher_catalog_error)
        } catch (error: Resources.NotFoundException) {
            launcherCatalogFailed(error)
            showLauncherRecovery(R.string.launcher_catalog_error)
        }
        return null
    }

    fun consumeHomeRequest(request: HomeRequest): Boolean {
        if (mutableHomeRequests.value != request) return false
        mutableHomeRequests.value = null
        return true
    }

    fun allowHomeHandoff(request: HomeRequest): Boolean =
        handoffGuard.allow(clock.now().elapsedRealtimeMillis, request.automatic)

    fun showLauncherRecovery(@StringRes message: Int) {
        automaticHandoffBlocked = true
        mutableState.update { it.copy(launcherRecovery = true, launcherError = message) }
    }

    fun dismissLauncherRecovery() {
        mutableState.update { it.copy(launcherRecovery = false) }
    }

    fun setRequireExitText(required: Boolean) {
        if (canEdit()) mutableState.update { it.copy(draftRequireExitText = required) }
    }

    fun toggleApp(packageName: String) {
        if (!canEdit()) return
        mutableState.update {
            it.copy(
                draftAllowed = if (packageName in it.draftAllowed) {
                    it.draftAllowed - packageName
                } else {
                    it.draftAllowed + packageName
                },
            )
        }
    }

    fun clearSelection() {
        if (canEdit()) {
            mutableState.update { it.copy(draftAllowed = emptySet()) }
            saveAppSelection()
        }
    }

    fun setSafetyAccepted(accepted: Boolean) {
        if (canEdit()) mutableState.update { it.copy(safetyAccepted = accepted) }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    private fun canEdit(): Boolean =
        state.value.session?.isActive == false && !state.value.busy && !state.value.storageError

    fun startFocus() {
        val current = state.value
        if (current.busy) return
        if (current.session == null || current.storageError) {
            notify(R.string.storage_error)
            return
        }
        if (!isDefaultHome()) {
            mutableState.update { it.copy(defaultHome = false, message = R.string.home_required) }
            return
        }
        if (!current.safetyAccepted) {
            notify(R.string.safety_required)
            return
        }
        if (!current.catalogLoaded || current.catalogError) {
            notify(R.string.catalog_error)
            return
        }
        if (!current.launcherCatalogLoaded || current.launcherCatalogError) {
            showLauncherRecovery(R.string.launcher_catalog_error)
            return
        }
        if (current.normalLauncher == null || current.normalLaunchers.none { it.target == current.normalLauncher }) {
            showLauncherRecovery(
                if (current.normalLauncher == null) R.string.normal_launcher_required else R.string.normal_launcher_unavailable,
            )
            return
        }
        mutate {
            store.startSession(
                allowedPackages = current.draftAllowed,
                requireExitText = current.draftRequireExitText,
                availablePackages = current.apps.map { it.packageName }.toSet(),
                now = clock.now(),
                plan = FocusPlan(
                    presetId = current.selectedPresetId,
                    intention = current.draftIntention,
                    durationMinutes = current.draftDurationMinutes,
                ),
            )
        }
    }

    fun endFocus(enteredText: String) {
        if (state.value.busy) return
        mutate { store.endSession(enteredText) }
    }

    private fun mutate(onSaved: () -> Unit = {}, action: suspend () -> SessionMutation) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            var outcome: SessionMutation? = null
            try {
                val result = action()
                outcome = result
                val message = when (result) {
                    SessionMutation.STARTED, SessionMutation.ENDED,
                    SessionMutation.CONFIGURATION_SAVED, SessionMutation.EXTENDED -> null
                    SessionMutation.ALREADY_ACTIVE -> R.string.already_active
                    SessionMutation.NOT_ACTIVE -> R.string.not_active
                    SessionMutation.NO_ALLOWED_APPS -> R.string.select_apps
                    SessionMutation.APPS_UNAVAILABLE -> R.string.apps_unavailable
                    SessionMutation.EXIT_TEXT_TOO_SHORT -> R.string.exit_text_too_short
                    SessionMutation.PRESET_NOT_FOUND -> R.string.preset_not_found
                    SessionMutation.INVALID_PRESET_NAME -> R.string.invalid_preset_name
                    SessionMutation.DUPLICATE_PRESET_NAME -> R.string.duplicate_preset_name
                    SessionMutation.LAST_PRESET -> R.string.last_preset
                    SessionMutation.INVALID_INTENTION -> R.string.invalid_intention
                    SessionMutation.INVALID_DURATION -> R.string.invalid_duration
                    SessionMutation.TIMER_NOT_FINISHED -> R.string.timer_not_finished
                }
                mutableState.update { it.copy(message = message) }
            } catch (error: IOException) {
                Log.e(TAG, "Unable to save the focus session; the previous state is unchanged.", error)
                notify(R.string.storage_write_error)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
            when (outcome) {
                SessionMutation.STARTED -> {
                    mutableHomeRequests.value = null
                    mutableState.update { it.copy(launcherRecovery = false, launcherError = null) }
                }
                SessionMutation.ENDED -> requestNormalHome()
                SessionMutation.CONFIGURATION_SAVED -> onSaved()
                else -> Unit
            }
        }
    }

    fun homeSelectionIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getApplication<Application>().getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    private fun isDefaultHome(): Boolean {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return home.resolveActivity(context.packageManager)?.packageName == context.packageName
    }

    fun launchApp(app: LaunchableApp) {
        val session = state.value.session
        if (session == null || state.value.storageError) {
            notify(R.string.storage_error)
            return
        }
        if (session.isActive && app.packageName !in session.allowedPackages) {
            notify(R.string.app_not_allowed)
            return
        }
        try {
            getApplication<Application>().getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, Process.myUserHandle(), null, null)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "The selected launchable activity is no longer available.", error)
            notify(R.string.app_launch_error)
            refresh()
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied launching the selected app.", error)
            notify(R.string.app_launch_error)
        }
    }

    fun openPhone() {
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_DIAL, "tel:".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No phone dialer is installed.", error)
            notify(R.string.phone_unavailable)
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied opening the phone dialer.", error)
            notify(R.string.phone_unavailable)
        }
    }

    fun notify(@StringRes message: Int) {
        mutableState.update { it.copy(message = message) }
    }

    fun consumeMessage(message: Int) {
        mutableState.update { if (it.message == message) it.copy(message = null) else it }
    }

    private companion object {
        const val TAG = "FocusHarbor"
    }
}
