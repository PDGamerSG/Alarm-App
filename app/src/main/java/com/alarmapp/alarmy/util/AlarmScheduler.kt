package com.alarmapp.alarmy.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.data.AlarmDatabase
import com.alarmapp.alarmy.receiver.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

object AlarmScheduler {

    const val CHANNEL_ALARM = "alarm_channel"
    const val CHANNEL_KEEPALIVE = "keepalive_channel"
    private const val LEGACY_NEXT_ALARM_CHANNEL = "next_alarm_channel"
    private const val LEGACY_NEXT_ALARM_NOTIFICATION_ID = 9999

    fun schedule(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextTriggerTime(alarm)

        // Use setAlarmClock for highest priority — survives Doze
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, getShowIntent(context))
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun getNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.isRepeating()) {
            // Find the next matching day
            for (i in 0..7) {
                val candidate = (alarmTime.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, i)
                }
                // Calendar: MONDAY=2, TUESDAY=3, ..., SUNDAY=1
                val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                val dayFlag = calendarDayToFlag(dayOfWeek)

                if (alarm.isDayEnabled(dayFlag)) {
                    if (i == 0 && candidate.timeInMillis <= now.timeInMillis) {
                        continue // Already passed today
                    }
                    return candidate.timeInMillis
                }
            }
        }

        // Non-repeating or fallback: next occurrence
        if (alarmTime.timeInMillis <= now.timeInMillis) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }
        return alarmTime.timeInMillis
    }

    private fun calendarDayToFlag(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> Alarm.MON
            Calendar.TUESDAY -> Alarm.TUE
            Calendar.WEDNESDAY -> Alarm.WED
            Calendar.THURSDAY -> Alarm.THU
            Calendar.FRIDAY -> Alarm.FRI
            Calendar.SATURDAY -> Alarm.SAT
            Calendar.SUNDAY -> Alarm.SUN
            else -> 0
        }
    }

    private fun getShowIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun rescheduleAllAlarms(context: Context) {
        withContext(Dispatchers.IO) {
            val dao = AlarmDatabase.getDatabase(context).alarmDao()
            val alarms = dao.getEnabledAlarms()
            for (alarm in alarms) {
                schedule(context, alarm)
            }
        }
    }

    fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Clean up legacy "Next alarm" notification + channel if present from older builds
        notificationManager.cancel(LEGACY_NEXT_ALARM_NOTIFICATION_ID)
        runCatching { notificationManager.deleteNotificationChannel(LEGACY_NEXT_ALARM_CHANNEL) }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm firing notifications"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val keepaliveChannel = NotificationChannel(
            CHANNEL_KEEPALIVE,
            "Alarm Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps alarms active in background"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(alarmChannel)
        notificationManager.createNotificationChannel(keepaliveChannel)
    }
}
