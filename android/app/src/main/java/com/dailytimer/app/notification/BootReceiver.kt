package com.dailytimer.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val pendingResult = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                NotificationScheduler.rescheduleAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
