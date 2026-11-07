package com.babygasu.nudge

/** Which capabilities this build variant contains (see the "capability" flavors in app/build.gradle.kts). */
object Features {
    /** Notification listener (automatic detection from messages and missed calls). */
    const val detection: Boolean = BuildConfig.DETECTION_AVAILABLE

    /** Floating bubble drawn over other apps. */
    const val bubble: Boolean = BuildConfig.BUBBLE_AVAILABLE
}
