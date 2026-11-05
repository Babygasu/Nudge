package com.babygasu.nudge.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.BuildConfig
import com.babygasu.nudge.bubble.BubbleService
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.ui.Format
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.Perms
import com.babygasu.nudge.ui.SectionHeader
import com.babygasu.nudge.ui.TestNotifications
import com.babygasu.nudge.ui.rememberResumeTick

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: NudgeViewModel) {
    val context = LocalContext.current
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val s = settingsState ?: return
    val tick = rememberResumeTick()
    val exact = remember(tick) { Perms.exactAlarmsAllowed(context) }
    val overlay = remember(tick) { Perms.overlayAllowed(context) }
    val battery = remember(tick) { Perms.batteryUnrestricted(context) }
    val notifications = remember(tick) { Perms.notificationsGranted(context) }
    val listener = remember(tick) { Perms.listenerGranted(context) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item { SectionHeader("Interruptions") }
            item { Column {
                Text("Alert me with sound for", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Priority.LOW to "All reminders",
                        Priority.MEDIUM to "Medium and up",
                        Priority.HIGH to "High and up",
                        Priority.CRITICAL to "Critical only",
                    ).forEach { (p, label) ->
                        FilterChip(selected = s.interruptFrom == p, onClick = { vm.setInterruptFrom(p) }, label = { Text(label) })
                    }
                }
                Text(
                    "Lower-priority reminders still arrive, silently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Tell me when Nudge notices something") },
                    supportingContent = { Text("A quiet notification with a \"Not a reminder\" button") },
                    trailingContent = { Switch(checked = s.detectionNotices, onCheckedChange = { vm.setDetectionNotices(it) }) },
                )
            }
            item { Column {
                Text("Snooze length", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { m ->
                        FilterChip(selected = s.snoozeMinutes == m, onClick = { vm.setSnoozeMinutes(m) }, label = { Text(Format.minutes(m)) })
                    }
                }
            } }

            item { SectionHeader("Floating bubble") }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Show the Nudge bubble") },
                    supportingContent = {
                        Text(
                            if (overlay) "A small movable bubble over other apps showing what's due today. Drag it to the bottom edge to hide it."
                            else "Needs \"Display over other apps\" permission",
                        )
                    },
                    trailingContent = {
                        Switch(checked = s.bubbleEnabled && overlay, onCheckedChange = { on ->
                            if (on && !overlay) {
                                Perms.openOverlaySettings(context)
                            } else {
                                vm.setBubbleEnabled(on)
                                if (on) BubbleService.start(context) else BubbleService.stop(context)
                            }
                        })
                    },
                )
            }

            item { SectionHeader("Reliability") }
            item {
                StatusRow("Notifications", notifications, "Reminders can alert you", "Reminders can't alert you") {
                    Perms.openNotificationSettings(context)
                }
            }
            item {
                StatusRow("Message detection", listener, "Notification access is on", "Notification access is off") {
                    Perms.openListenerSettings(context)
                }
            }
            item {
                StatusRow("On-time reminders", exact, "Exact alarms allowed", "May arrive a few minutes late") {
                    Perms.openExactAlarmSettings(context)
                }
            }
            item {
                StatusRow("Battery", battery, "Unrestricted", "Optimised: your phone may pause Nudge") {
                    Perms.openBatterySettings(context)
                }
            }

            item { SectionHeader("Test detection") }
            item {
                Text(
                    "Send yourself a sample message notification to check the whole pipeline. Needs notifications and notification access to be on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TestNotifications.samples.forEachIndexed { index, sample ->
                item {
                    OutlinedButton(
                        onClick = {
                            if (!notifications || !listener) {
                                Toast.makeText(context, "Turn on notifications and message detection first", Toast.LENGTH_LONG).show()
                            } else {
                                TestNotifications.post(context, index)
                            }
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    ) { Text("“$sample”", maxLines = 2) }
                }
            }

            item { SectionHeader("About") }
            item {
                Text(
                    "Nudge ${BuildConfig.VERSION_NAME}\nOn-device only. No account, no analytics, no internet access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, badText: String, onFix: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (ok) "✓ $okText" else badText,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = { TextButton(onClick = onFix) { Text(if (ok) "Manage" else "Fix") } },
    )
}
