package com.babygasu.nudge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.babygasu.nudge.intelligence.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "nudge_settings")

data class NudgeSettings(
    val onboardingDone: Boolean = false,
    val monitoringPaused: Boolean = false,
    /** Off by default: titles say "them" and no sender name is stored. */
    val storeSenderNames: Boolean = false,
    /** Tell the user when Nudge creates a reminder from a notification. */
    val detectionNotices: Boolean = true,
    /** Reminders below this priority arrive silently. */
    val interruptFrom: Priority = Priority.LOW,
    val snoozeMinutes: Int = 60,
    val bubbleEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val PAUSED = booleanPreferencesKey("monitoring_paused")
        val SENDER_NAMES = booleanPreferencesKey("store_sender_names")
        val NOTICES = booleanPreferencesKey("detection_notices")
        val INTERRUPT_FROM = stringPreferencesKey("interrupt_from")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val BUBBLE = booleanPreferencesKey("bubble_enabled")
    }

    val flow: Flow<NudgeSettings> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            NudgeSettings(
                onboardingDone = p[Keys.ONBOARDED] ?: false,
                monitoringPaused = p[Keys.PAUSED] ?: false,
                storeSenderNames = p[Keys.SENDER_NAMES] ?: false,
                detectionNotices = p[Keys.NOTICES] ?: true,
                interruptFrom = p[Keys.INTERRUPT_FROM]?.let { v -> Priority.entries.firstOrNull { it.name == v } } ?: Priority.LOW,
                snoozeMinutes = p[Keys.SNOOZE] ?: 60,
                bubbleEnabled = p[Keys.BUBBLE] ?: false,
            )
        }

    suspend fun current(): NudgeSettings = flow.first()

    suspend fun setOnboardingDone(value: Boolean) = context.settingsStore.edit { it[Keys.ONBOARDED] = value }
    suspend fun setMonitoringPaused(value: Boolean) = context.settingsStore.edit { it[Keys.PAUSED] = value }
    suspend fun setStoreSenderNames(value: Boolean) = context.settingsStore.edit { it[Keys.SENDER_NAMES] = value }
    suspend fun setDetectionNotices(value: Boolean) = context.settingsStore.edit { it[Keys.NOTICES] = value }
    suspend fun setInterruptFrom(value: Priority) = context.settingsStore.edit { it[Keys.INTERRUPT_FROM] = value.name }
    suspend fun setSnoozeMinutes(value: Int) = context.settingsStore.edit { it[Keys.SNOOZE] = value }
    suspend fun setBubbleEnabled(value: Boolean) = context.settingsStore.edit { it[Keys.BUBBLE] = value }
    suspend fun clear() = context.settingsStore.edit { it.clear() }
}
