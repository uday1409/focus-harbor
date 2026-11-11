package dev.focusharbor.app.focus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "focus_session",
    produceMigrations = { listOf(FocusStore.exitPolicyMigration) },
)
