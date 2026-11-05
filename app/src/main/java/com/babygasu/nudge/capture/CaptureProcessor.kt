package com.babygasu.nudge.capture

import com.babygasu.nudge.data.IngestOutcome
import com.babygasu.nudge.data.MonitoredAppsRepository
import com.babygasu.nudge.data.Origin
import com.babygasu.nudge.data.ReminderRepository
import com.babygasu.nudge.data.SettingsRepository
import com.babygasu.nudge.data.SourceMeta
import com.babygasu.nudge.intelligence.AnalysisInput
import com.babygasu.nudge.intelligence.Candidate
import com.babygasu.nudge.intelligence.LocalEngine
import com.babygasu.nudge.intelligence.SourceKind
import com.babygasu.nudge.reminders.ReminderNotifier
import com.babygasu.nudge.security.KeyManager
import com.babygasu.nudge.security.SafeLog
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The ephemeral processing pipeline:
 *  1. receive captured text (memory only) → 2. analyse locally → 3. build structured reminders →
 *  4. save and verify → 5. drop the text (it goes out of scope here and is never persisted).
 */
class CaptureProcessor(
    private val engine: LocalEngine,
    private val settings: SettingsRepository,
    private val apps: MonitoredAppsRepository,
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val keys: KeyManager,
) {
    private val dayFirstDates: Boolean get() = Locale.getDefault().country != "US"

    suspend fun process(item: CapturedNotification) {
        val s = settings.current()
        if (s.monitoringPaused) return
        if (!item.isTest) {
            apps.recordSeen(item.packageName, item.appLabel)
            if (!apps.isEnabled(item.packageName)) return
        }
        val sender = item.senderName?.takeIf { it.isNotBlank() }
        val input = AnalysisInput(
            text = item.text,
            senderName = if (s.storeSenderNames) sender else null,
            sourceKind = item.kind,
            authoredByUser = item.fromUser,
            now = ZonedDateTime.now(),
            dayFirstDates = dayFirstDates,
        )
        val candidates = engine.analyze(input)
        SafeLog.event("capture", "analysed", "candidates" to candidates.size)
        if (candidates.isEmpty()) return

        val source = SourceMeta(
            origin = Origin.NOTIFICATION,
            appLabel = item.appLabel,
            packageName = item.packageName,
            senderDisplay = if (s.storeSenderNames) sender else null,
            senderKey = sender?.let { keys.senderKey(item.packageName, it) },
        )
        val outcomes = reminders.ingest(candidates, source)
        if (s.detectionNotices) outcomes.forEach(notifier::showDetected)
    }

    /** Text the user pasted or shared into Nudge. Analysed in memory; nothing is saved until they confirm. */
    fun analyzeText(text: String): List<Candidate> = engine.analyze(
        AnalysisInput(text = text, sourceKind = SourceKind.SHARED_BY_USER, now = ZonedDateTime.now(), dayFirstDates = dayFirstDates),
    )

    suspend fun saveShared(candidates: List<Candidate>): List<IngestOutcome> =
        reminders.ingest(candidates, SourceMeta(Origin.SHARED, "Shared by you", null, null, null))
}
