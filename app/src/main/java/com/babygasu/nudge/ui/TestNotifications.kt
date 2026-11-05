package com.babygasu.nudge.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.babygasu.nudge.R
import com.babygasu.nudge.reminders.Channels

/**
 * Lets you test the full pipeline (notification → detection → reminder) without asking anyone
 * to message you. The listener treats these as coming from "Nudge test".
 */
object TestNotifications {
    val samples = listOf(
        "Please send me the report before Friday.",
        "I was expecting the report yesterday. We cannot proceed without it.",
        "Call me when you are free. It's important.",
        "Can we meet tomorrow at 10?",
        "lol did you see the match last night?",
    )

    @SuppressLint("MissingPermission")
    fun post(context: Context, index: Int) {
        val text = samples[index]
        val user = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName("Ama (test)").build()
        val style = NotificationCompat.MessagingStyle(user)
            .addMessage(text, System.currentTimeMillis(), sender)
        val notification = NotificationCompat.Builder(context, Channels.TEST)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setContentTitle("Ama (test)")
            .setContentText(text)
            .setStyle(style)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify("test", 1000 + index, notification)
    }
}
