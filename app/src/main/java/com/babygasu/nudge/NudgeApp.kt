package com.babygasu.nudge

import android.app.Application
import android.content.Context
import com.babygasu.nudge.reminders.Channels
import com.babygasu.nudge.reminders.ReconcileWorker
import kotlinx.coroutines.launch

class NudgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.create(this)
        ReconcileWorker.schedule(this)
        // Re-arm alarms whenever the process starts (cheap, and covers missed broadcasts).
        container.appScope.launch {
            container.reminders.rescheduleAll()
            container.reminders.flagPossibleDuplicates()
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as NudgeApp).container
