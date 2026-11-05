package com.babygasu.nudge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.babygasu.nudge.intelligence.Priority

private val Light = lightColorScheme(
    primary = Color(0xFF2F6F6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E4),
    onPrimaryContainer = Color(0xFF0B2B28),
    secondary = Color(0xFF586572),
    secondaryContainer = Color(0xFFDDE5EC),
    onSecondaryContainer = Color(0xFF16202A),
    background = Color(0xFFF7F8F6),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFF7F8F6),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE3E8E5),
    onSurfaceVariant = Color(0xFF59615E),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F0),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD1C9),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF1F4F4B),
    onPrimaryContainer = Color(0xFFCDE8E4),
    secondary = Color(0xFFBCC8D3),
    secondaryContainer = Color(0xFF3A4651),
    onSecondaryContainer = Color(0xFFDDE5EC),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D201F),
    error = Color(0xFFF2B8B5),
)

@Composable
fun NudgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun priorityColor(p: Priority, dark: Boolean): Color = when (p) {
    Priority.CRITICAL -> if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    Priority.HIGH -> if (dark) Color(0xFFFFB870) else Color(0xFFC0661A)
    Priority.MEDIUM -> if (dark) Color(0xFF8FD1C9) else Color(0xFF2F6F6A)
    Priority.LOW -> if (dark) Color(0xFF8F9895) else Color(0xFF8A938F)
}
