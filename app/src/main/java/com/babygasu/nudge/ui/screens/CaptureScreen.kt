package com.babygasu.nudge.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.babygasu.nudge.intelligence.Candidate
import com.babygasu.nudge.ui.Format
import com.babygasu.nudge.ui.NudgeViewModel
import com.babygasu.nudge.ui.PriorityLabel

/**
 * Paste or share a message to turn it into reminders. The text is held only in this screen's
 * memory (not saved instance state) and is cleared after saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(vm: NudgeViewModel, onBack: () -> Unit, onManual: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(vm.consumeSharedText().orEmpty()) }
    var results by remember { mutableStateOf<List<Candidate>?>(null) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }

    fun analyse() {
        val found = vm.analyze(text)
        results = found
        selected = found.indices.toSet()
    }
    // Shared text: analyse straight away.
    LaunchedEffect(Unit) { if (text.isNotBlank()) analyse() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Turn a message into reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Paste a message, or use Share → Nudge in any app. Nudge reads it on this phone, keeps only the reminders you choose, and forgets the text.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; results = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                minLines = 4,
            )
            Button(onClick = { analyse() }, enabled = text.isNotBlank()) { Text("Find reminders") }

            val found = results
            if (found != null && found.isEmpty()) {
                Text("Nudge didn't find a clear request, promise or deadline in this message.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { onManual(text.lineSequence().firstOrNull().orEmpty().take(80)) }) {
                    Text("Create a reminder myself")
                }
            }
            if (!found.isNullOrEmpty()) {
                found.forEachIndexed { index, c ->
                    CandidateCard(c, checked = index in selected) { on ->
                        selected = if (on) selected + index else selected - index
                    }
                }
                Button(
                    onClick = {
                        val chosen = found.filterIndexed { i, _ -> i in selected }
                        vm.saveCandidates(chosen)
                        text = ""
                        results = null
                        Toast.makeText(context, "Saved ${chosen.size} reminder(s)", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    enabled = selected.isNotEmpty(),
                ) { Text("Save selected") }
            }
        }
    }
}

@Composable
private fun CandidateCard(c: Candidate, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Column(Modifier.padding(start = 4.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityLabel(c.priority)
                    val due = Format.due(c.dueAt?.toInstant()?.toEpochMilli(), c.duePrecision, System.currentTimeMillis())
                    if (due != null) Text("  ·  $due", style = MaterialTheme.typography.bodySmall)
                }
                c.signals.forEach {
                    Text("• ${it.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
