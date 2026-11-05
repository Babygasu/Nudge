package com.babygasu.nudge.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.capture.KnownApps
import com.babygasu.nudge.ui.InfoCard
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.Perms
import com.babygasu.nudge.ui.rememberResumeTick

private const val STEPS = 6

/**
 * Every step explains what the permission is for and can be skipped. Nudge stays useful with
 * none of them: reminders you add yourself still work.
 */
@Composable
fun OnboardingScreen(vm: NudgeViewModel) {
    var step by remember { mutableIntStateOf(0) }
    val next: () -> Unit = { if (step < STEPS - 1) step++ else vm.finishOnboarding() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> WelcomeStep()
                1 -> NotificationsStep()
                2 -> DetectionStep()
                3 -> AppsStep(vm)
                4 -> SenderStep(vm)
                else -> TimingStep()
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = next) { Text(if (step == STEPS - 1) "Start using Nudge" else "Continue") }
        }
    }
}

@Composable
private fun ColumnScope.Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.WelcomeStep() {
    Heading(
        "We remember what matters.\nNot everything you say.",
        "Nudge notices requests, promises and deadlines in your messages and turns them into calm, prioritised reminders.",
    )
    InfoCard(
        "How your privacy is protected",
        "• Everything happens on this phone. This version of Nudge has no internet access at all.\n" +
            "• Messages are read in memory, turned into a reminder, then forgotten. Nudge never stores message text.\n" +
            "• Reminders are encrypted on your phone and excluded from backups.\n" +
            "• Nudge never changes or deletes anything in your other apps.",
    )
}

@Composable
private fun ColumnScope.NotificationsStep() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var granted by remember(tick) { mutableStateOf(Perms.notificationsGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Heading("Let Nudge remind you", "Nudge needs permission to show notifications so your reminders can reach you on time.")
    if (granted) {
        Text("✓ Notifications are allowed", color = MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Perms.openNotificationSettings(context)
        }) { Text("Allow notifications") }
        Text(
            "Without this, reminders still appear inside Nudge but can't alert you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.DetectionStep() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val granted = remember(tick) { Perms.listenerGranted(context) }
    Heading(
        "Spot requests in your messages",
        "With notification access, Nudge reads the notifications of apps you choose (next step) and looks for things you need to act on.",
    )
    InfoCard(
        "What Nudge can and can't see",
        "Nudge only sees what appears in a notification: usually the sender and a message preview. It can't see muted chats, chats you have open, " +
            "or messages you send (unless the app shows your reply in the notification). For anything it misses, use Share → Nudge.",
    )
    if (granted) {
        Text("✓ Notification access is on", color = MaterialTheme.colorScheme.primary)
    } else {
        Button(onClick = { Perms.openListenerSettings(context) }) { Text("Turn on detection") }
        InfoCard(
            "If the switch is greyed out (\"Restricted setting\")",
            "Android blocks this for apps installed outside the Play Store until you allow it:\n" +
                "1. Tap \"Open app info\" below.\n" +
                "2. Tap ⋮ (top-right) → \"Allow restricted settings\" and confirm.\n" +
                "3. Come back and tap \"Turn on detection\" again.\n" +
                "On some phones the ⋮ option only appears after you've tried once.",
        ) { OutlinedButton(onClick = { Perms.openAppInfo(context) }) { Text("Open app info") } }
        Text(
            "You can skip this. Reminders you add or share still work.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.AppsStep(vm: NudgeViewModel) {
    val context = LocalContext.current
    val installed = remember { KnownApps.installed(context) }
    var chosen by remember { mutableStateOf(installed.map { it.packageName }.toSet()) }
    Heading("Choose which apps to read", "Nudge ignores every app you don't tick. You can change this anytime in the Privacy Centre.")
    if (installed.isEmpty()) {
        Text("None of the usual messaging apps were found. Apps will appear in the Privacy Centre after they send a notification.")
    }
    installed.forEach { app ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = app.packageName in chosen,
                onCheckedChange = { on -> chosen = if (on) chosen + app.packageName else chosen - app.packageName },
            )
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    // Choices are saved as you tick them.
    LaunchedEffect(chosen) { if (installed.isNotEmpty()) vm.applyAppChoices(installed, chosen) }
}

@Composable
private fun ColumnScope.SenderStep(vm: NudgeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Heading(
        "Show who reminders are from?",
        "If you turn this on, Nudge stores the sender's name with each reminder (\"Send Ama the report\"). If it's off, Nudge writes \"them\" and keeps only an unreadable code to avoid duplicates.",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Store sender names", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = settings?.storeSenderNames == true, onCheckedChange = { vm.setStoreSenderNames(it) })
    }
}

@Composable
private fun ColumnScope.TimingStep() {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val exact = remember(tick) { Perms.exactAlarmsAllowed(context) }
    val battery = remember(tick) { Perms.batteryUnrestricted(context) }
    Heading("Make reminders reliable", "Two optional settings help reminders arrive on time, even after a restart.")
    InfoCard(
        if (exact) "✓ On-time reminders allowed" else "On-time reminders",
        "Lets Nudge alert you at the exact minute. If you don't allow it, Android may deliver reminders a few minutes late.",
    ) { if (!exact) OutlinedButton(onClick = { Perms.openExactAlarmSettings(context) }) { Text("Allow") } }
    InfoCard(
        if (battery) "✓ Battery optimisation off for Nudge" else "Battery optimisation",
        "Some phones (especially Samsung and Xiaomi) stop background apps. Setting Nudge to \"Unrestricted\" keeps detection running.",
    ) { if (!battery) OutlinedButton(onClick = { Perms.openBatterySettings(context) }) { Text("Open battery settings") } }
}
