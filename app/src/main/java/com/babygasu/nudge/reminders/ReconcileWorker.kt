package com.babygasu.nudge.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.babygasu.nudge.container
import java.util.concurrent.TimeUnit

/**
 * Safety net: every few hours, re-arm all alarms. If an alarm was lost (aggressive battery
 * management on some phones), any overdue reminder that was never shown is shown now.
 */
class ReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.reminders.rescheduleAll()
        return Result.success()
    }

    companion object {
        private const val NAME = "nudge-reconcile"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReconcileWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
