package com.babygasu.nudge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.Features
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.ui.HomeSections
import com.babygasu.nudge.ui.InfoCard
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.Perms
import com.babygasu.nudge.ui.ReminderRow
import com.babygasu.nudge.ui.SectionHeader
import com.babygasu.nudge.ui.rememberResumeTick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: NudgeViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onCapture: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val all by vm.reminders.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val accessGranted = remember(tick) { Perms.listenerGranted(context) }
    val sections = remember(all, now) { HomeSections.build(all, now) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Nudge") },
                actions = {
                    IconButton(onClick = onCapture) { Icon(Icons.Filled.Edit, contentDescription = "Turn a message into a reminder") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") })
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Greeting(now, sections) }
            if (!Features.detection) {
                // Nudge Core: no banner nagging about a capability this build doesn't have.
            } else if (!accessGranted) {
                item {
                    InfoCard(
                        "Message detection is off",
                        "Nudge can only spot requests and deadlines in messages after you allow notification access. Reminders you add yourself work without it.",
                    ) { OutlinedButton(onClick = onOpenPrivacy) { Text("Set up detection") } }
                }
            } else if (settings?.monitoringPaused == true) {
                item {
                    InfoCard("Detection is paused", "Nudge isn't reading any notifications right now.") {
                        Button(onClick = { vm.setPaused(false) }) { Text("Resume") }
                    }
                }
            }
            if (sections.isEmpty) {
                item { EmptyState(onCapture) }
            }
            section("Overdue", sections.overdue, now, onOpen, vm)
            section("Today", sections.today, now, onOpen, vm)
            section("Coming up this week", sections.upcoming, now, onOpen, vm)
            section("No date yet", sections.undated, now, onOpen, vm)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    items: List<Reminder>,
    now: Long,
    onOpen: (Long) -> Unit,
    vm: NudgeViewModel,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, items.size) }
    items(items, key = { "$title-${it.id}" }) { r ->
        ReminderRow(r, now, onClick = { onOpen(r.id) }, onToggleDone = { vm.complete(r.id) })
    }
}

@Composable
private fun Greeting(now: Long, sections: HomeSections) {
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val hello = when (time.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(time)
    val summary = when {
        sections.overdue.isNotEmpty() && sections.today.isNotEmpty() ->
            "${sections.overdue.size} overdue and ${sections.today.size} due today."
        sections.overdue.isNotEmpty() -> "${sections.overdue.size} ${plural(sections.overdue.size)} overdue."
        sections.today.isNotEmpty() -> "${sections.today.size} ${plural(sections.today.size)} due today."
        sections.isEmpty -> "Nothing needs your attention."
        else -> "Nothing due today."
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hello, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(2.dp))
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun plural(n: Int) = if (n == 1) "thing" else "things"

@Composable
private fun EmptyState(onCapture: () -> Unit) {
    InfoCard(
        "You're all caught up",
        "When someone asks you for something, Nudge will add it here. You can also share a message to Nudge from any app, or paste one in.",
        modifier = Modifier.padding(top = 12.dp),
    ) { OutlinedButton(onClick = onCapture) { Text("Paste a message") } }
}
