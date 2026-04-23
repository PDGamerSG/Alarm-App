package com.alarmapp.alarmy.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.max
import kotlin.math.min
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.data.AlarmDatabase
import com.alarmapp.alarmy.ui.AlarmActivity
import com.alarmapp.alarmy.util.AlarmScheduler
import kotlinx.coroutines.*

class AlarmService : Service() {

    inner class AlarmBinder : Binder() {
        fun setVolume(volume: Float) {
            val clamped = min(1f, max(0f, volume))
            mediaPlayer?.setVolume(clamped, clamped)
        }
    }

    private val binder = AlarmBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder = binder

    private var isConfirmation = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra("alarm_id", -1L) ?: -1L
        isConfirmation = intent?.getBooleanExtra("is_confirmation", false) ?: false
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val dao = AlarmDatabase.getDatabase(applicationContext).alarmDao()
            val alarm = dao.getAlarmById(alarmId)
            if (alarm == null) {
                stopSelf()
                return@launch
            }

            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, buildNotification(alarm))
                startAlarmPlayback(alarm)
                launchAlarmActivity(alarm)
            }

            // Reschedule repeating alarm
            if (alarm.isRepeating()) {
                AlarmScheduler.schedule(applicationContext, alarm)
            } else {
                // Disable one-time alarm
                dao.update(alarm.copy(isEnabled = false))
            }
        }

        return START_STICKY
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("alarm_id", alarm.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarm.id.toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (alarm.label.isNotBlank()) alarm.label else "Alarm"
        val title = if (isConfirmation) "⏰ Are you still awake?" else label
        val text = if (isConfirmation) "Confirmation check — dismiss to prove you're up!" else "Alarm is ringing!"

        return NotificationCompat.Builder(this, AlarmScheduler.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun startAlarmPlayback(alarm: Alarm) {
        // Set alarm stream to max volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        val ringtoneUri = if (alarm.ringtoneUri.isNotBlank()) {
            Uri.parse(alarm.ringtoneUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to default alarm
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, defaultUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
            }
        }

        if (alarm.vibrateEnabled) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun launchAlarmActivity(alarm: Alarm) {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("difficulty", alarm.difficulty)
            putExtra("snooze_minutes", alarm.snoozeMinutes)
            putExtra("alarm_label", alarm.label)
            putExtra("is_confirmation", isConfirmation)
            putExtra("confirmation_enabled", alarm.confirmationEnabled)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
