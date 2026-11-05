package com.babygasu.nudge.capture

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import com.babygasu.nudge.intelligence.SourceKind

/** Apps Nudge suggests during setup. The user decides which ones it may read. */
object KnownApps {
    data class App(val packageName: String, val label: String, val kind: SourceKind)

    val ALL: List<App> = listOf(
        App("com.whatsapp", "WhatsApp", SourceKind.MESSAGE),
        App("com.whatsapp.w4b", "WhatsApp Business", SourceKind.MESSAGE),
        App("org.telegram.messenger", "Telegram", SourceKind.MESSAGE),
        App("org.thoughtcrime.securesms", "Signal", SourceKind.MESSAGE),
        App("com.facebook.orca", "Messenger", SourceKind.MESSAGE),
        App("com.google.android.apps.messaging", "Google Messages", SourceKind.MESSAGE),
        App("com.samsung.android.messaging", "Samsung Messages", SourceKind.MESSAGE),
        App("com.google.android.gm", "Gmail", SourceKind.EMAIL),
        App("com.microsoft.office.outlook", "Outlook", SourceKind.EMAIL),
        App("com.samsung.android.email.provider", "Samsung Email", SourceKind.EMAIL),
        App("com.Slack", "Slack", SourceKind.MESSAGE),
        App("com.microsoft.teams", "Microsoft Teams", SourceKind.MESSAGE),
        App("com.linkedin.android", "LinkedIn", SourceKind.MESSAGE),
        App("com.google.android.dialer", "Phone (missed calls)", SourceKind.MESSAGE),
        App("com.samsung.android.dialer", "Samsung Phone (missed calls)", SourceKind.MESSAGE),
        App("com.samsung.android.incallui", "Samsung call screen (missed calls)", SourceKind.MESSAGE),
        App("com.android.server.telecom", "System calls (missed calls)", SourceKind.MESSAGE),
        App("com.android.dialer", "Phone (missed calls)", SourceKind.MESSAGE),
    )
    private val byPackage = ALL.associateBy { it.packageName }

    fun kindOf(packageName: String, category: String?): SourceKind =
        byPackage[packageName]?.kind ?: if (category == Notification.CATEGORY_EMAIL) SourceKind.EMAIL else SourceKind.MESSAGE

    fun label(context: Context, packageName: String): String =
        byPackage[packageName]?.label ?: runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    @Suppress("DEPRECATION")
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        return ALL.filter {
            try {
                pm.getApplicationInfo(it.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
