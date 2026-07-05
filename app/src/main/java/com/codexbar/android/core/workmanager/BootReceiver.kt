package com.codexbar.android.core.workmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codexbar.android.core.security.EncryptedPrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefsManager = EncryptedPrefsManager(context.applicationContext)
            WorkManagerInitializer.scheduleConfiguredRefresh(context, prefsManager)
        }
    }
}
