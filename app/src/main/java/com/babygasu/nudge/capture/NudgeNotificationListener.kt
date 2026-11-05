package com.babygasu.nudge.capture

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.babygasu.nudge.container
import com.babygasu.nudge.security.SafeLog
import kotlinx.coroutines.launch

/**
 * Receives notifications the user has allowed Nudge to see. Nudge never dismisses, edits or
 * replies to notifications, and never touches the original messages in other apps.
 */
class NudgeNotificationListener : NotificationListenerService() {

    /** In-memory only: notification keys, timestamps and hashes so updates aren't re-read. */
    private val seen = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 300
    }

    override fun onListenerConnected() {
        container.listenerConnected.value = true
        SafeLog.event("listener", "connected")
    }

    override fun onListenerDisconnected() {
        container.listenerConnected.value = false
        SafeLog.event("listener", "disconnected")
        // Ask Android to reconnect us (it can drop listeners under memory pressure).
        requestRebind(ComponentName(this, NudgeNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val items = try {
            NotificationExtractor.extract(this, sbn, seen)
        } catch (e: Exception) {
            SafeLog.error("listener", "extract_failed", e)
            return
        }
        if (items.isEmpty()) return
        val processor = container.captureProcessor
        container.appScope.launch {
            items.forEach { item ->
                try {
                    processor.process(item)
                } catch (e: Exception) {
                    SafeLog.error("listener", "process_failed", e)
                }
            }
        }
    }
}
