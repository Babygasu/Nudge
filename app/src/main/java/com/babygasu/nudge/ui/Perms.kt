package com.babygasu.nudge.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.babygasu.nudge.capture.NudgeNotificationListener
import com.babygasu.nudge.container

/** Permission checks and the system screens where the user grants them. */
object Perms {
    fun notificationsGranted(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun listenerGranted(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun exactAlarmsAllowed(c: Context): Boolean = c.container.scheduler.canScheduleExact()

    fun overlayAllowed(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    fun openListenerSettings(c: Context) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(c, NudgeNotificationListener::class.java).flattenToString(),
            )
        } else null
        if (detail == null || !launch(c, detail)) launch(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAppInfo(c: Context) {
        launch(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))
    }

    fun openNotificationSettings(c: Context) {
        val ok = launch(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))
        if (!ok) openAppInfo(c)
    }

    fun openExactAlarmSettings(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ok = launch(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")))
            if (!ok) openAppInfo(c)
        }
    }

    fun openOverlaySettings(c: Context) {
        val ok = launch(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")))
        if (!ok) openAppInfo(c)
    }

    fun openBatterySettings(c: Context) {
        if (!launch(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppInfo(c)
    }

    private fun launch(c: Context, intent: Intent): Boolean =
        runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** Increments every time the screen resumes — use as a key to re-check permissions after Settings. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}
