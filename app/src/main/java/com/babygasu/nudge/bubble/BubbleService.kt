package com.babygasu.nudge.bubble

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.babygasu.nudge.MainActivity
import com.babygasu.nudge.container
import com.babygasu.nudge.security.SafeLog
import com.babygasu.nudge.ui.HomeSections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Optional floating bubble showing how many reminders need attention today.
 * Drag to move; drag to the bottom edge to hide; tap to open Nudge.
 * Requires the user to grant "Display over other apps".
 */
class BubbleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bubble: TextView? = null
    private lateinit var windowManager: WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, container.notifier.bubbleServiceNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            SafeLog.error("bubble", "foreground_refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val density = resources.displayMetrics.density
        val size = (56 * density).toInt()
        val view = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2F6F6A.toInt())
            }
            elevation = 8 * density
            text = "•"
            contentDescription = "Nudge: open reminders"
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (12 * density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.35).toInt()
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        openApp()
                    } else if (e.rawY > resources.displayMetrics.heightPixels - 120 * density) {
                        stopSelf() // dragged to the bottom edge: hide until Nudge is opened again
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, params)
        bubble = view

        scope.launch {
            container.reminders.observeAll().collect { all ->
                val count = HomeSections.build(all, System.currentTimeMillis()).attentionCount
                view.text = if (count > 0) count.toString() else "✓"
                view.contentDescription = if (count > 0) "Nudge: $count reminders need attention" else "Nudge: nothing due"
            }
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.babygasu.nudge.action.STOP_BUBBLE"
        private const val NOTIFICATION_ID = 7001

        /** Call only while Nudge is on screen (Android restricts starting this from the background). */
        fun start(context: Context) {
            if (!com.babygasu.nudge.Features.bubble || !Settings.canDrawOverlays(context)) return
            runCatching { context.startForegroundService(Intent(context, BubbleService::class.java)) }
                .onFailure { SafeLog.error("bubble", "start_failed", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
