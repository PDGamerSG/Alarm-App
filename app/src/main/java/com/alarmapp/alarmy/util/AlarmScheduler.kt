package com.alarmapp.alarmy.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.data.AlarmDatabase
import com.alarmapp.alarmy.receiver.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

object AlarmScheduler {

    const val CHANNEL_ALARM = "alarm_channel"
    const val CHANNEL_NEXT_ALARM = "next_alarm_channel"
    const val CHANNEL_KEEPALIVE = "keepalive_channel"
    const val NEXT_ALARM_NOTIFICATION_ID = 9999

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

    suspend fun updateNextAlarmNotification(context: Context) {
        withContext(Dispatchers.IO) {
            val dao = AlarmDatabase.getDatabase(context).alarmDao()
            val enabledAlarms = dao.getEnabledAlarms()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (enabledAlarms.isEmpty()) {
                notificationManager.cancel(NEXT_ALARM_NOTIFICATION_ID)
                return@withContext
            }

            // Find the nearest alarm
            val now = System.currentTimeMillis()
            val nearest = enabledAlarms
                .map { it to getNextTriggerTime(it) }
                .minByOrNull { it.second }
                ?: return@withContext

            val alarm = nearest.first
            val triggerTime = nearest.second

            val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
            val timeStr = String.format(
                "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
            )

            val label = if (alarm.label.isNotBlank()) " — ${alarm.label}" else ""

            val notification = NotificationCompat.Builder(context, CHANNEL_NEXT_ALARM)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Next alarm: $timeStr$label")
                .setContentText(alarm.getRepeatDaysText())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setContentIntent(getShowIntent(context))
                .build()

            notificationManager.notify(NEXT_ALARM_NOTIFICATION_ID, notification)
        }
    }

    fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm firing notifications"
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val nextAlarmChannel = NotificationChannel(
            CHANNEL_NEXT_ALARM,
            "Next Alarm",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification showing next alarm"
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
        notificationManager.createNotificationChannel(nextAlarmChannel)
        notificationManager.createNotificationChannel(keepaliveChannel)
    }
}
