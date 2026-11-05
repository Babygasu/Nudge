package com.babygasu.nudge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.babygasu.nudge.bubble.BubbleService
import com.babygasu.nudge.ui.NudgeRoot
import com.babygasu.nudge.ui.theme.NudgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            NudgeTheme {
                NudgeRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The bubble can only be (re)started while Nudge is in the foreground.
        lifecycleScope.launch {
            if (container.settings.current().bubbleEnabled) BubbleService.start(this@MainActivity)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            // Held in memory only, until the capture screen reads it.
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { container.sharedText.value = it }
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id > 0) {
            container.openReminderRequest.value = id
            intent.removeExtra(EXTRA_REMINDER_ID)
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "open_reminder_id"
    }
}
