package com.babygasu.nudge.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.data.Origin
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.data.effectiveTime
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.ReminderRow

private enum class StatusFilter(val label: String) { OPEN("Open"), CLOSED("Done & dismissed"), ALL("All") }
private enum class OriginFilter(val label: String) { ANY("Any source"), DETECTED("Noticed by Nudge"), MINE("Added by me") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRemindersScreen(vm: NudgeViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val all by vm.reminders.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(StatusFilter.OPEN) }
    var origin by rememberSaveable { mutableStateOf(OriginFilter.ANY) }
    var priorities by remember { mutableStateOf(emptySet<Priority>()) }

    val filtered = remember(all, query, status, origin, priorities) {
        all.asSequence()
            .filter {
                when (status) {
                    StatusFilter.OPEN -> it.status == ReminderStatus.OPEN
                    StatusFilter.CLOSED -> it.status != ReminderStatus.OPEN
                    StatusFilter.ALL -> true
                }
            }
            .filter {
                when (origin) {
                    OriginFilter.ANY -> true
                    OriginFilter.DETECTED -> it.origin != Origin.MANUAL
                    OriginFilter.MINE -> it.origin == Origin.MANUAL
                }
            }
            .filter { priorities.isEmpty() || it.priority in priorities }
            .filter {
                query.isBlank() || listOfNotNull(it.title, it.notes, it.sourceApp, it.senderDisplay)
                    .any { field -> field.contains(query.trim(), ignoreCase = true) }
            }
            .sortedWith(
                compareBy<com.babygasu.nudge.data.Reminder> { it.status != ReminderStatus.OPEN }
                    .thenBy { if (it.status == ReminderStatus.OPEN) it.effectiveTime ?: Long.MAX_VALUE else -(it.completedAt ?: 0L) }
                    .thenByDescending { it.priority },
            )
            .toList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("All reminders") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add reminder") }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search reminders") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusFilter.entries.forEach { f ->
                    FilterChip(selected = status == f, onClick = { status = f }, label = { Text(f.label) })
                }
                VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically))
                Priority.entries.reversed().forEach { p ->
                    FilterChip(
                        selected = p in priorities,
                        onClick = { priorities = if (p in priorities) priorities - p else priorities + p },
                        label = { Text(p.label) },
                    )
                }
                VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically))
                OriginFilter.entries.forEach { f ->
                    FilterChip(selected = origin == f, onClick = { origin = f }, label = { Text(f.label) })
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    if (all.isEmpty()) "No reminders yet." else "No reminders match these filters.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { r ->
                    ReminderRow(
                        r, now,
                        onClick = { onOpen(r.id) },
                        onToggleDone = { if (r.status == ReminderStatus.OPEN) vm.complete(r.id) else vm.reopen(r.id) },
                    )
                }
            }
        }
    }
}
