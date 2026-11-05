package com.babygasu.nudge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babygasu.nudge.AppContainer
import com.babygasu.nudge.capture.KnownApps
import com.babygasu.nudge.data.MonitoredApp
import com.babygasu.nudge.data.NudgeSettings
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.intelligence.Candidate
import com.babygasu.nudge.intelligence.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One view model for the app's screens. Writes run on the app-wide scope so they finish
 * even if the user navigates away mid-action.
 */
class NudgeViewModel(private val c: AppContainer) : ViewModel() {
    val reminders: StateFlow<List<Reminder>> =
        c.reminders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val apps: StateFlow<List<MonitoredApp>> =
        c.monitoredApps.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<NudgeSettings?> =
        c.settings.flow.stateIn<NudgeSettings?>(viewModelScope, SharingStarted.Eagerly, null)
    val listenerConnected: StateFlow<Boolean> = c.listenerConnected
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun observe(id: Long): Flow<Reminder?> = c.reminders.observe(id)

    fun save(r: Reminder) = io { c.reminders.save(r) }
    fun complete(id: Long) = io { c.reminders.complete(id) }
    fun reopen(id: Long) = io { c.reminders.reopen(id) }
    fun dismiss(id: Long) = io { c.reminders.dismiss(id) }
    fun delete(id: Long) = io { c.reminders.delete(id) }
    fun snooze(id: Long, until: Long) = io { c.reminders.snooze(id, until) }
    fun deleteClosed() = io { c.reminders.deleteClosed() }
    fun deleteAllData() = io { c.deleteAllData() }

    fun analyze(text: String): List<Candidate> = c.captureProcessor.analyzeText(text)
    fun saveCandidates(list: List<Candidate>) = io { c.captureProcessor.saveShared(list) }
    fun consumeSharedText(): String? = c.sharedText.value.also { c.sharedText.value = null }

    fun setPaused(v: Boolean) = io { c.settings.setMonitoringPaused(v) }
    fun setStoreSenderNames(v: Boolean) = io { c.settings.setStoreSenderNames(v) }
    fun setDetectionNotices(v: Boolean) = io { c.settings.setDetectionNotices(v) }
    fun setInterruptFrom(p: Priority) = io { c.settings.setInterruptFrom(p) }
    fun setSnoozeMinutes(m: Int) = io { c.settings.setSnoozeMinutes(m) }
    fun setBubbleEnabled(v: Boolean) = io { c.settings.setBubbleEnabled(v) }
    fun finishOnboarding() = io { c.settings.setOnboardingDone(true) }

    fun setAppEnabled(packageName: String, label: String, enabled: Boolean) =
        io { c.monitoredApps.setEnabled(packageName, label, enabled) }

    fun applyAppChoices(apps: List<KnownApps.App>, enabled: Set<String>) = io {
        apps.forEach { c.monitoredApps.setEnabled(it.packageName, it.label, it.packageName in enabled) }
    }

    fun listInstalledApps(apps: List<KnownApps.App>) = io {
        apps.forEach { c.monitoredApps.listIfAbsent(it.packageName, it.label) }
    }

    private fun io(block: suspend () -> Unit) {
        c.appScope.launch { block() }
    }
}
