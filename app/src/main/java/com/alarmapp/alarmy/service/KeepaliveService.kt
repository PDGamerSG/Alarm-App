package com.alarmapp.alarmy.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.ui.MainActivity
import com.alarmapp.alarmy.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps the app out of Android's "stopped state".
 *
 * Why this matters:
 * - Android 3.1+ prevents broadcast receivers from firing if the app is in stopped state
 *   (which happens after a force-stop). A running foreground service keeps the app active.
 * - stopWithTask=false (set in manifest) ensures this service survives task removal
 *   (swiping app from recents).
 * - START_STICKY means the OS restarts it automatically if killed by memory pressure.
 * - setAlarmClock alarms are shown in the system status bar and fire at highest priority;
 *   on most OEM ROMs they bypass stopped-state checks.
 *
 * On a true user-initiated force-stop (Settings > Apps > Force Stop), Android kills
 * every process of the package — no app can fully escape that. However, this service
 * makes casual kills and swipe-dismissals non-fatal.
 */
class KeepaliveService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Re-schedule any alarms that may have been lost while the service was down
        serviceScope.launch {
            AlarmScheduler.rescheduleAllAlarms(applicationContext)
        }

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlarmScheduler.CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Alarmy is active")
            .setContentText("Alarms will fire on time")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped from recents — restart self immediately so alarms keep firing
        val restartIntent = Intent(applicationContext, KeepaliveService::class.java)
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Restart self if destroyed unexpectedly
        val restartIntent = Intent(applicationContext, KeepaliveService::class.java)
        startForegroundService(restartIntent)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, KeepaliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepaliveService::class.java))
        }
    }
}
