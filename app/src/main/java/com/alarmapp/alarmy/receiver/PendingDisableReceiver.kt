package com.alarmapp.alarmy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmapp.alarmy.data.AlarmDatabase
import com.alarmapp.alarmy.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PendingDisableReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)
        if (alarmId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AlarmDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getAlarmById(alarmId) ?: return@launch
                // Only disable if still pending (could have been cancelled)
                if (alarm.pendingDisableAt > 0L && alarm.isEnabled) {
                    val updated = alarm.copy(isEnabled = false, pendingDisableAt = 0L)
                    dao.update(updated)
                    AlarmScheduler.cancel(context, updated)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
