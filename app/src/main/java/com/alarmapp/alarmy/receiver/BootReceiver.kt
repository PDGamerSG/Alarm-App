package com.alarmapp.alarmy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmapp.alarmy.service.KeepaliveService
import com.alarmapp.alarmy.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // Start persistent keepalive service first
            KeepaliveService.start(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.rescheduleAllAlarms(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
