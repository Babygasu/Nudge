package com.babygasu.nudge

import android.content.Context
import android.content.Intent
import com.babygasu.nudge.bubble.BubbleService
import com.babygasu.nudge.capture.CaptureProcessor
import com.babygasu.nudge.data.MonitoredAppsRepository
import com.babygasu.nudge.data.NudgeDatabase
import com.babygasu.nudge.data.ReminderRepository
import com.babygasu.nudge.data.SettingsRepository
import com.babygasu.nudge.intelligence.LocalEngine
import com.babygasu.nudge.reminders.ReminderNotifier
import com.babygasu.nudge.reminders.ReminderScheduler
import com.babygasu.nudge.security.KeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual dependency wiring. Phase 1 deliberately avoids a DI framework: the graph is small
 * and this keeps the build simple. Everything is created lazily on first use.
 */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val keyManager by lazy { KeyManager(context) }
    val database by lazy { NudgeDatabase.build(context, keyManager.databasePassphrase()) }
    val settings by lazy { SettingsRepository(context) }
    val scheduler by lazy { ReminderScheduler(context) }
    val notifier by lazy { ReminderNotifier(context) }
    val reminders by lazy { ReminderRepository(database, scheduler, notifier, settings) }
    val monitoredApps by lazy { MonitoredAppsRepository(database.monitoredAppDao()) }
    val engine by lazy { LocalEngine() }
    val captureProcessor by lazy {
        CaptureProcessor(engine, settings, monitoredApps, reminders, notifier, keyManager)
    }

    /** In-memory UI signals. Shared text lives here only until the capture screen reads it. */
    val listenerConnected = MutableStateFlow(false)
    val sharedText = MutableStateFlow<String?>(null)
    val openReminderRequest = MutableStateFlow<Long?>(null)

    /** "Delete all Nudge data": reminders, app choices, settings and the sender-pseudonym key. */
    suspend fun deleteAllData() {
        reminders.deleteAll()
        monitoredApps.clearCache()
        settings.clear()
        keyManager.rotateSenderKey()
        context.stopService(Intent(context, BubbleService::class.java))
    }
}
