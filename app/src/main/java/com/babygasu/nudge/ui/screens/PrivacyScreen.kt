package com.babygasu.nudge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.capture.KnownApps
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.ui.InfoCard
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.Perms
import com.babygasu.nudge.ui.SectionHeader
import com.babygasu.nudge.ui.rememberResumeTick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(vm: NudgeViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val connected by vm.listenerConnected.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val accessGranted = remember(tick) { Perms.listenerGranted(context) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmClosed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.listInstalledApps(KnownApps.installed(context)) }

    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Privacy Centre") }) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                InfoCard(
                    "Local only",
                    "This version of Nudge has no internet permission, so nothing can leave your phone. Message text is analysed in memory and discarded; only reminders are kept, encrypted, and never included in backups.",
                )
            }

            item { SectionHeader("Message detection") }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Notification access") },
                    supportingContent = {
                        Text(
                            when {
                                !accessGranted -> "Off. Nudge can't read any notifications."
                                connected -> "On and running"
                                else -> "Allowed, waiting for Android to connect"
                            },
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { Perms.openListenerSettings(context) }) { Text(if (accessGranted) "Manage" else "Turn on") }
                    },
                )
            }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Pause all detection") },
                    supportingContent = { Text("Nudge ignores every notification while paused") },
                    trailingContent = {
                        Switch(checked = settings?.monitoringPaused == true, onCheckedChange = { vm.setPaused(it) })
                    },
                )
            }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Store sender names") },
                    supportingContent = { Text("Off: titles say \"them\" and no names are saved") },
                    trailingContent = {
                        Switch(checked = settings?.storeSenderNames == true, onCheckedChange = { vm.setStoreSenderNames(it) })
                    },
                )
            }

            item { SectionHeader("Apps Nudge may read") }
            if (apps.isEmpty()) {
                item {
                    Text(
                        "No apps yet. Apps appear here once installed or after they send a notification. Every app is off until you switch it on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    colors = transparent,
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(if (app.enabled) "Reading notifications" else "Ignored") },
                    trailingContent = {
                        Switch(checked = app.enabled, onCheckedChange = { vm.setAppEnabled(app.packageName, app.label, it) })
                    },
                )
            }

            item { SectionHeader("Processing") }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("On-device intelligence") },
                    supportingContent = { Text("Always on. Works offline.") },
                )
            }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Cloud AI") },
                    supportingContent = { Text("Off. Not part of this version. If it's added later it will be optional, and Nudge will ask first and explain exactly what is sent.") },
                )
            }
            item {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Connected services") },
                    supportingContent = { Text("None. Calendar, Google Tasks and Microsoft To Do come in a later phase.") },
                )
            }

            item { SectionHeader("What Nudge keeps") }
            item {
                val open = reminders.count { it.status == ReminderStatus.OPEN }
                val closed = reminders.size - open
                Text(
                    "$open open and $closed completed/dismissed reminders, stored encrypted on this phone. For each: the action, due time, priority, status, " +
                        "the app it came from, the reasons it was created, and the sender's name only if you turned that on. Never the message itself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                OutlinedButton(onClick = { confirmClosed = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Delete completed and dismissed")
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
            item {
                Button(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all Nudge data") }
            }
            item {
                Text(
                    "Removes every reminder, your app choices and settings from this phone. Messages in WhatsApp, Gmail and other apps are never touched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (confirmClosed) {
        AlertDialog(
            onDismissRequest = { confirmClosed = false },
            title = { Text("Delete finished reminders?") },
            text = { Text("Completed and dismissed reminders will be permanently removed.") },
            confirmButton = { TextButton(onClick = { confirmClosed = false; vm.deleteClosed() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClosed = false }) { Text("Cancel") } },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all Nudge data?") },
            text = { Text("All reminders, app choices and settings will be permanently erased from this phone, and Nudge will start fresh.") },
            confirmButton = { TextButton(onClick = { confirmWipe = false; vm.deleteAllData() }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
