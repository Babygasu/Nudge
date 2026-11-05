package com.babygasu.nudge.security

import android.util.Log
import com.babygasu.nudge.BuildConfig

/**
 * The only logging API Nudge code should use. It accepts fixed event codes and numbers — there is
 * deliberately no way to pass free text, so message content cannot end up in logs. Exception
 * messages are also dropped (they can contain data); only the exception type is recorded.
 */
object SafeLog {
    fun event(area: String, code: String, vararg numbers: Pair<String, Number>) {
        if (!BuildConfig.DEBUG) return
        val details = numbers.joinToString(" ") { "${it.first}=${it.second}" }
        Log.d("Nudge/$area", if (details.isEmpty()) code else "$code $details")
    }

    fun error(area: String, code: String, error: Throwable? = null) {
        Log.w("Nudge/$area", if (error == null) code else "$code (${error.javaClass.simpleName})")
    }
}
