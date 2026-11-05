package com.babygasu.nudge.capture

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.babygasu.nudge.intelligence.SourceKind
import com.babygasu.nudge.reminders.Channels

/**
 * The only object that ever holds captured text. It exists in memory for the few milliseconds it
 * takes to analyse, is never written to disk or logged, and is dropped right after.
 */
data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val senderName: String?,
    val text: String,
    val kind: SourceKind,
    val fromUser: Boolean,
    val isTest: Boolean,
) {
    override fun toString(): String = "CapturedNotification(package=$packageName, kind=$kind)" // never print text
}

object NotificationExtractor {
    private val MISSED_CALL = Regex("""missed\s+(?:voice\s+|video\s+|whatsapp\s+)?calls?""", RegexOption.IGNORE_CASE)
    private val COUNT_SUFFIX = Regex("""\s*\(\d+\s+(?:new\s+)?messages?\)\s*$""", RegexOption.IGNORE_CASE)
    private val SKIP_CATEGORIES = setOf(
        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STATUS,
        Notification.CATEGORY_ALARM, Notification.CATEGORY_PROMO, Notification.CATEGORY_RECOMMENDATION,
    )

    /**
     * @param seen in-memory map (owned by the listener) used to avoid re-reading the same
     * notification when an app updates it. Holds keys, timestamps and hashes — no text.
     */
    fun extract(context: Context, sbn: StatusBarNotification, seen: MutableMap<String, Long>): List<CapturedNotification> {
        val n = sbn.notification ?: return emptyList()
        val own = context.packageName
        val isTest = sbn.packageName == own && n.channelId == Channels.TEST
        if (sbn.packageName == own && !isTest) return emptyList()
        if (sbn.isOngoing) return emptyList()
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return emptyList()
        if (n.category in SKIP_CATEGORIES) return emptyList()
        val extras = n.extras ?: return emptyList()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val label = if (isTest) "Nudge test" else KnownApps.label(context, sbn.packageName)
        val kind = KnownApps.kindOf(sbn.packageName, n.category)

        // Missed calls: Android exposes these as ordinary notifications from the phone app,
        // so no call-log permission is needed.
        val missedCategory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && n.category == Notification.CATEGORY_MISSED_CALL
        val titleSaysMissed = title != null && MISSED_CALL.containsMatchIn(title)
        val textSaysMissed = text != null && MISSED_CALL.containsMatchIn(text) && kind != SourceKind.EMAIL
        if (missedCategory || titleSaysMissed || textSaysMissed) {
            val caller = if (titleSaysMissed) text?.takeUnless { MISSED_CALL.containsMatchIn(it) } else title
            val key = "call:${sbn.key}:${(title + text).hashCode()}"
            if (!markSeen(seen, key, sbn.postTime)) return emptyList()
            return listOf(CapturedNotification(sbn.packageName, label, caller?.let(::cleanSender), "Missed call", SourceKind.MISSED_CALL, false, isTest))
        }

        // Conversations (WhatsApp, Messages, Telegram…): read only messages we haven't seen yet.
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null && style.messages.isNotEmpty()) {
            val lastSeen = seen[sbn.key] ?: 0L
            val fresh = style.messages.filter { it.timestamp > lastSeen }.takeLast(3)
            if (fresh.isEmpty()) return emptyList()
            seen[sbn.key] = fresh.maxOf { it.timestamp }
            val userName = style.user.name?.toString()
            val conversation = style.conversationTitle?.toString() ?: title
            return fresh.mapNotNull { m ->
                val body = m.text?.toString()?.trim().orEmpty()
                if (body.isEmpty()) return@mapNotNull null
                val personName = m.person?.name?.toString()
                val fromUser = m.person == null || (userName != null && personName == userName)
                val sender = if (fromUser) conversation else personName ?: title
                CapturedNotification(
                    sbn.packageName, label, sender?.let(::cleanSender), body,
                    if (kind == SourceKind.EMAIL) SourceKind.EMAIL else SourceKind.MESSAGE, fromUser, isTest,
                )
            }
        }

        // Everything else (e.g. email: title = sender, text = subject and preview).
        val body = bigText ?: text ?: return emptyList()
        val key = "${sbn.key}|${body.hashCode()}"
        if (!markSeen(seen, key, sbn.postTime)) return emptyList()
        return listOf(CapturedNotification(sbn.packageName, label, title?.let(::cleanSender), body, kind, false, isTest))
    }

    fun cleanSender(raw: String): String = raw.replace(COUNT_SUFFIX, "").trim()

    private fun markSeen(seen: MutableMap<String, Long>, key: String, time: Long): Boolean {
        if (seen.containsKey(key)) return false
        seen[key] = time
        return true
    }
}
