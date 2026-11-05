package com.babygasu.nudge.ui.screens

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babygasu.nudge.data.Origin
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.data.signalSet
import com.babygasu.nudge.intelligence.DuePrecision
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.ui.Format
import com.babygasu.nudge.ui.NudgeViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReminderEditScreen(vm: NudgeViewModel, id: Long, initialTitle: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val existingState: State<Reminder?> =
        if (id > 0) remember(id) { vm.observe(id) }.collectAsStateWithLifecycle(initialValue = null)
        else remember { mutableStateOf(null) }
    val existing = existingState.value
    val now by vm.now.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: initialTitle.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var due by remember(existing?.id) { mutableStateOf(existing?.dueAt) }
    var priority by remember(existing?.id) { mutableStateOf(existing?.priority ?: Priority.MEDIUM) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var snoozeMenu by remember { mutableStateOf(false) }

    fun defaultDue(): Long = LocalDate.now(zone).plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    fun save() {
        val t = title.trim()
        if (t.isEmpty()) {
            Toast.makeText(context, "Give the reminder a title", Toast.LENGTH_SHORT).show()
            return
        }
        val base = existing
        val reminder = if (base == null) {
            Reminder(
                title = t,
                notes = notes.trim().ifEmpty { null },
                dueAt = due,
                duePrecision = due?.let { DuePrecision.EXACT },
                priority = priority,
                priorityByUser = true,
                origin = Origin.MANUAL,
            )
        } else {
            val timeChanged = due != base.dueAt
            base.copy(
                title = t,
                notes = notes.trim().ifEmpty { null },
                dueAt = due,
                duePrecision = if (timeChanged) due?.let { DuePrecision.EXACT } else base.duePrecision,
                snoozedUntil = if (timeChanged) null else base.snoozedUntil,
                priority = priority,
                priorityByUser = base.priorityByUser || priority != base.priority,
            )
        }
        vm.save(reminder)
        onBack()
    }

    if (id > 0 && existing == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Column {
                Text("This reminder isn't available any more.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New reminder" else "Reminder") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What needs doing?") },
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("When", style = MaterialTheme.typography.labelLarge)
                Row {
                    OutlinedButton(onClick = { showDate = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val shownPrecision = if (existing != null && due == existing.dueAt) existing.duePrecision else DuePrecision.EXACT
                        Text(Format.due(due, shownPrecision, now) ?: "Add date and time")
                    }
                    if (due != null) {
                        IconButton(onClick = { due = null }) { Icon(Icons.Filled.Close, contentDescription = "Remove date") }
                    }
                }
            }

            Column {
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.reversed().forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (existing != null) {
                if (existing.status == ReminderStatus.OPEN) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.complete(existing.id); onBack() }) { Text("Mark done") }
                        Box {
                            OutlinedButton(onClick = { snoozeMenu = true }) { Text("Snooze") }
                            DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                                snoozeOptions(zone).forEach { (label, until) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        snoozeMenu = false
                                        vm.snooze(existing.id, until)
                                        onBack()
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { vm.dismiss(existing.id); onBack() }) {
                            Text(if (existing.origin == Origin.MANUAL) "Dismiss" else "Not a reminder")
                        }
                    }
                } else {
                    Button(onClick = { vm.reopen(existing.id) }) { Text("Reopen") }
                }
                if (existing.origin != Origin.MANUAL) WhyCard(existing)
                Text(
                    "Created ${Format.timestamp(existing.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDate) {
        val initialUtc = Instant.ofEpochMilli(due ?: defaultDue()).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = due?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.of(9, 0)
                        due = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                    showTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val current = Instant.ofEpochMilli(due ?: defaultDue()).atZone(zone)
        val state = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val day = Instant.ofEpochMilli(due ?: defaultDue()).atZone(zone).toLocalDate()
                    due = day.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("It will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(existing.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun snoozeOptions(zone: ZoneId): List<Pair<String, Long>> {
    val now = System.currentTimeMillis()
    val tomorrowMorning = LocalDate.now(zone).plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    return listOf(
        "15 minutes" to now + 15 * 60_000L,
        "1 hour" to now + 60 * 60_000L,
        "3 hours" to now + 3 * 60 * 60_000L,
        "Tomorrow morning" to tomorrowMorning,
    )
}

@Composable
private fun WhyCard(r: Reminder) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Why Nudge noticed this", style = MaterialTheme.typography.titleSmall)
            r.signalSet.forEach { Text("• ${it.description}", style = MaterialTheme.typography.bodyMedium) }
            val source = listOfNotNull(r.sourceApp, r.senderDisplay).joinToString(" · ")
            if (source.isNotEmpty()) Text("From: $source", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nudge didn't keep the original message, only this reminder. If it got something wrong, edit it or tap \"Not a reminder\"; corrections are recorded so detection can improve in a later update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
