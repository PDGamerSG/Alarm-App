package com.alarmapp.alarmy.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.game.MemoryGameView
import com.alarmapp.alarmy.service.AlarmService
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity(), MemoryGameView.GameListener {

    private lateinit var memoryGameView: MemoryGameView
    private lateinit var tvTime: TextView
    private lateinit var tvLabel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAttempts: TextView
    private lateinit var tvWrong: TextView
    private lateinit var btnSnooze: TextView
    private lateinit var tvRoundInfo: TextView

    private var snoozeMinutes: Int = 0
    private var alarmId: Long = -1L
    private val timer = Timer()

    private val totalRounds = 3
    private var currentRound = 1
    private var baseDifficulty = Alarm.DIFFICULTY_EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()
        setContentView(R.layout.activity_alarm)
        applyImmersiveMode()

        alarmId = intent.getLongExtra("alarm_id", -1L)
        val difficulty = intent.getIntExtra("difficulty", Alarm.DIFFICULTY_EASY)
        snoozeMinutes = intent.getIntExtra("snooze_minutes", 0)
        val label = intent.getStringExtra("alarm_label") ?: ""

        tvTime = findViewById(R.id.tvAlarmTime)
        tvLabel = findViewById(R.id.tvAlarmLabel)
        tvStatus = findViewById(R.id.tvGameStatus)
        tvAttempts = findViewById(R.id.tvAttempts)
        tvWrong = findViewById(R.id.tvWrong)
        btnSnooze = findViewById(R.id.btnSnooze)
        tvRoundInfo = findViewById(R.id.tvRoundInfo)
        memoryGameView = findViewById(R.id.memoryGameView)

        tvLabel.text = if (label.isNotBlank()) label else "Alarm"

        baseDifficulty = difficulty
        currentRound = 1
        memoryGameView.difficulty = baseDifficulty
        memoryGameView.listener = this
        updateRoundDisplay()

        // Snooze button
        if (snoozeMinutes > 0) {
            btnSnooze.text = "Snooze ($snoozeMinutes min)"
            btnSnooze.visibility = android.view.View.VISIBLE
            btnSnooze.setOnClickListener { snooze() }
        } else {
            btnSnooze.visibility = android.view.View.GONE
        }

        // Update clock
        updateTime()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread { updateTime() }
            }
        }, 1000, 1000)

        // Start the game after a short delay
        memoryGameView.postDelayed({
            memoryGameView.startGame()
        }, 1000)
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun updateRoundDisplay() {
        tvRoundInfo.text = "ROUND $currentRound OF $totalRounds"
    }

    private fun updateTime() {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        tvTime.text = format.format(Date())
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onGameComplete() {
        if (currentRound < totalRounds) {
            currentRound++
            tvStatus.text = "ROUND COMPLETE! GET READY..."
            tvWrong.visibility = View.GONE
            updateRoundDisplay()
            // Bump difficulty by 1 per round, capped at 9
            memoryGameView.difficulty = (baseDifficulty + currentRound - 1).coerceAtMost(9)
            memoryGameView.postDelayed({
                memoryGameView.reset()
                memoryGameView.postDelayed({
                    tvStatus.text = "GET READY..."
                    memoryGameView.startGame()
                }, 800)
            }, 1500)
        } else {
            tvStatus.text = "ALARM DISMISSED!"
            tvRoundInfo.text = "ALL DONE!"
            tvWrong.visibility = View.GONE
            dismissAlarm()
        }
    }

    override fun onWrongAttempt(attemptCount: Int) {
        tvAttempts.text = "Attempts: $attemptCount"
        tvWrong.visibility = android.view.View.VISIBLE
        tvWrong.text = "WRONG!"
        tvWrong.alpha = 1f
        tvWrong.animate().alpha(0f).setDuration(1500).start()
    }

    override fun onSequenceShowStart() {
        tvStatus.text = "WATCH THE PATTERN"
    }

    override fun onSequenceShowEnd() {
        tvStatus.text = "YOUR TURN - TAP THE SEQUENCE"
    }

    private fun snooze() {
        if (snoozeMinutes > 0) {
            // Reschedule alarm for snooze
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = android.content.Intent(this, com.alarmapp.alarmy.receiver.AlarmReceiver::class.java).apply {
                putExtra("alarm_id", alarmId)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this, (alarmId + 10000).toInt(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
            val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(triggerTime, null)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

            Toast.makeText(this, "Snoozed for $snoozeMinutes minutes", Toast.LENGTH_SHORT).show()
            dismissAlarm()
        }
    }

    private fun dismissAlarm() {
        AlarmService.stop(this)
        timer.cancel()
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back button during alarm
    }

    override fun onDestroy() {
        timer.cancel()
        super.onDestroy()
    }
}
