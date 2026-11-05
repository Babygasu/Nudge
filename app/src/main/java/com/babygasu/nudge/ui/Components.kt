package com.babygasu.nudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.babygasu.nudge.data.Origin
import com.babygasu.nudge.data.Reminder
import com.babygasu.nudge.data.ReminderStatus
import com.babygasu.nudge.data.effectiveTime
import com.babygasu.nudge.intelligence.Priority
import com.babygasu.nudge.ui.theme.priorityColor

@Composable
fun PriorityLabel(p: Priority) {
    val color = priorityColor(p, isSystemInDarkTheme())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(p.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun ReminderRow(r: Reminder, now: Long, onClick: () -> Unit, onToggleDone: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val open = r.status == ReminderStatus.OPEN
    val overdue = open && (r.effectiveTime ?: Long.MAX_VALUE) < now
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(4.dp).fillMaxHeight()
                    .background(if (open) priorityColor(r.priority, dark) else MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (r.status == ReminderStatus.DONE) TextDecoration.LineThrough else null,
                    color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val meta = listOfNotNull(
                    Format.reminderDue(r, now),
                    when (r.status) {
                        ReminderStatus.DONE -> "Done"
                        ReminderStatus.DISMISSED -> "Dismissed"
                        ReminderStatus.OPEN -> null
                    },
                    r.sourceApp?.takeIf { r.origin != Origin.MANUAL },
                    r.senderDisplay,
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (open) {
                        PriorityLabel(r.priority)
                        if (meta.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(
                onClick = onToggleDone,
                modifier = Modifier.semantics { contentDescription = if (open) "Mark done" else "Reopen" },
            ) {
                Icon(if (open) Icons.Filled.Check else Icons.Filled.Refresh, contentDescription = null)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (count != null) Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}
