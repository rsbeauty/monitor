package com.screenmirror.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences("admin_lock_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_paired_lifetime", false)) {
                val serviceIntent = Intent(context, ScreenShareService::class.java).apply {
                    action = "BOOT_STANDBY"
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
