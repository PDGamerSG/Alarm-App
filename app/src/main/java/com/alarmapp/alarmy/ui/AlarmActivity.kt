package com.alarmapp.alarmy.ui

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
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
import com.alarmapp.alarmy.util.NfcTagUtils
import java.text.SimpleDateFormat
import java.util.*

class AlarmActivity : AppCompatActivity(), MemoryGameView.GameListener {

    private lateinit var memoryGameView: MemoryGameView
    private lateinit var tvTime: TextView
    private lateinit var tvLabel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvNfcHint: TextView
    private lateinit var tvAttempts: TextView
    private lateinit var tvWrong: TextView
    private lateinit var btnSnooze: TextView
    private lateinit var tvRoundInfo: TextView

    private var snoozeMinutes: Int = 0
    private var alarmId: Long = -1L
    private var isConfirmation: Boolean = false
    private var confirmationEnabled: Boolean = false
    private var nfcTagId: String = ""
    private var dismissMode: Int = Alarm.DISMISS_GAME
    private var challengeComplete = false
    private var nfcDismissed = false
    private val requiresNfc: Boolean
        get() = dismissMode != Alarm.DISMISS_GAME && nfcTagId.isNotBlank()
    private val usesMemoryGame: Boolean
        get() = dismissMode != Alarm.DISMISS_NFC
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag ->
        val scannedId = NfcTagUtils.canonicalId(tag)
        runOnUiThread {
            if (nfcDismissed || isFinishing || isDestroyed) return@runOnUiThread
            if (!requiresNfc) return@runOnUiThread
            if (nfcTagId.isNotBlank() && scannedId == nfcTagId) {
                if (dismissMode == Alarm.DISMISS_GAME_AND_NFC && !challengeComplete) {
                    tvStatus.text = "COMPLETE THE MEMORY GAME, THEN TAP THE NFC TAG"
                    Toast.makeText(this, "Finish the memory game first", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                nfcDismissed = true
                tvStatus.text = "NFC TAG ACCEPTED — ALARM DISMISSED"
                tvNfcHint.visibility = View.GONE
                tvRoundInfo.text = "NFC DISMISS"
                tvWrong.visibility = View.GONE
                memoryGameView.isEnabled = false
                if (confirmationEnabled && !isConfirmation) {
                    scheduleConfirmationAlarm()
                }
                animateVolume(0f, durationMs = 250)
                memoryGameView.postDelayed({ dismissAlarm() }, 250)
            } else if (nfcTagId.isNotBlank()) {
                Toast.makeText(this, "This tag is not assigned to this alarm", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val timer = Timer()
    private var countdownTimer: CountDownTimer? = null

    private val totalRounds = 3
    private var currentRound = 1
    private var baseDifficulty = Alarm.DIFFICULTY_EASY

    // Volume control
    private var alarmBinder: AlarmService.AlarmBinder? = null
    private var currentVolume = 1.0f
    private var volumeAnimator: ValueAnimator? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            alarmBinder = service as? AlarmService.AlarmBinder
            alarmBinder?.setVolume(1.0f)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            alarmBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowFlags()
        setContentView(R.layout.activity_alarm)
        applyImmersiveMode()

        alarmId = intent.getLongExtra("alarm_id", -1L)
        val difficulty = intent.getIntExtra("difficulty", Alarm.DIFFICULTY_EASY)
        snoozeMinutes = intent.getIntExtra("snooze_minutes", 0)
        val label = intent.getStringExtra("alarm_label") ?: ""
        isConfirmation = intent.getBooleanExtra("is_confirmation", false)
        confirmationEnabled = intent.getBooleanExtra("confirmation_enabled", false)
        nfcTagId = intent.getStringExtra("nfc_tag_id")?.uppercase(Locale.ROOT) ?: ""
        val configuredDismissMode = intent.getIntExtra(
            "dismiss_mode",
            if (nfcTagId.isNotBlank()) Alarm.DISMISS_NFC else Alarm.DISMISS_GAME
        )
        dismissMode = when (configuredDismissMode) {
            Alarm.DISMISS_NFC, Alarm.DISMISS_GAME_AND_NFC -> configuredDismissMode
            else -> Alarm.DISMISS_GAME
        }
        // A malformed/old configuration must always retain a usable dismiss path.
        if (nfcTagId.isBlank() && dismissMode != Alarm.DISMISS_GAME) {
            dismissMode = Alarm.DISMISS_GAME
        }

        tvTime = findViewById(R.id.tvAlarmTime)
        tvLabel = findViewById(R.id.tvAlarmLabel)
        tvStatus = findViewById(R.id.tvGameStatus)
        tvNfcHint = findViewById(R.id.tvNfcHint)
        tvAttempts = findViewById(R.id.tvAttempts)
        tvWrong = findViewById(R.id.tvWrong)
        btnSnooze = findViewById(R.id.btnSnooze)
        tvRoundInfo = findViewById(R.id.tvRoundInfo)
        memoryGameView = findViewById(R.id.memoryGameView)
        tvNfcHint.visibility = if (requiresNfc) View.VISIBLE else View.GONE
        if (requiresNfc) {
            tvNfcHint.text = if (dismissMode == Alarm.DISMISS_GAME_AND_NFC) {
                "Complete the game, then tap the assigned NFC tag to dismiss"
            } else {
                "Tap the assigned NFC tag to dismiss"
            }
        }

        tvLabel.text = if (isConfirmation) "Are you still awake?"
                       else if (label.isNotBlank()) label else "Alarm"

        baseDifficulty = difficulty
        currentRound = 1
        memoryGameView.difficulty = baseDifficulty
        memoryGameView.listener = this
        updateRoundDisplay()
        if (!usesMemoryGame) {
            memoryGameView.visibility = View.GONE
            tvAttempts.visibility = View.GONE
            tvRoundInfo.text = "NFC DISMISS"
        }

        // Snooze button — hidden during confirmation checks
        if (snoozeMinutes > 0 && !isConfirmation) {
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

        // Countdown from 5 before game starts — gives user time to wake up.
        if (usesMemoryGame) {
            startGameCountdown()
        } else {
            tvStatus.text = "TAP THE ASSIGNED NFC TAG TO DISMISS"
        }
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

    /** Volume at which a round starts — decreases each round as reward for progress */
    private fun roundStartVolume(round: Int): Float = when (round) {
        1 -> 1.0f
        2 -> 0.75f
        else -> 0.5f
    }

    private fun animateVolume(target: Float, durationMs: Long = 600) {
        volumeAnimator?.cancel()
        volumeAnimator = ValueAnimator.ofFloat(currentVolume, target).apply {
            duration = durationMs
            addUpdateListener {
                currentVolume = it.animatedValue as Float
                alarmBinder?.setVolume(currentVolume)
            }
            start()
        }
    }

    private fun snapVolume(target: Float) {
        volumeAnimator?.cancel()
        currentVolume = target
        alarmBinder?.setVolume(currentVolume)
    }

    private fun updateTime() {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        tvTime.text = format.format(Date())
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, AlarmService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        runCatching { unbindService(serviceConnection) }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (requiresNfc) enableNfcReaderMode()
    }

    override fun onPause() {
        disableNfcReaderMode()
        super.onPause()
    }

    private fun enableNfcReaderMode() {
        if (!requiresNfc) return
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        runCatching {
            adapter.enableReaderMode(
                this,
                nfcReaderCallback,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
    }

    private fun disableNfcReaderMode() {
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // If the activity was kept around and a new alarm (possibly the confirmation)
        // was delivered via SINGLE_TOP, restart cleanly with the new extras so the
        // "Are you still awake?" state is actually applied.
        if (intent != null) {
            setIntent(intent)
            recreate()
        }
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
            // Reward: set volume to next round's base (lower than current)
            animateVolume(roundStartVolume(currentRound), durationMs = 800)
            memoryGameView.postDelayed({
                memoryGameView.reset()
                memoryGameView.postDelayed({
                    tvStatus.text = "GET READY..."
                    memoryGameView.startGame()
                }, 800)
            }, 1500)
        } else {
            challengeComplete = true
            if (dismissMode == Alarm.DISMISS_GAME_AND_NFC) {
                tvStatus.text = "GAME COMPLETE — TAP THE ASSIGNED NFC TAG"
                tvRoundInfo.text = "NOW TAP NFC"
                tvNfcHint.visibility = View.VISIBLE
                tvNfcHint.text = "Tap the assigned NFC tag to finish dismissing"
                tvWrong.visibility = View.GONE
                // Keep the alarm audible until the required NFC proof is supplied.
                return
            }
            tvStatus.text = "ALARM DISMISSED!"
            tvRoundInfo.text = "ALL DONE!"
            tvWrong.visibility = View.GONE
            // Schedule confirmation immediately — don't wait for the fade-out runnable,
            // which can be dropped if the view detaches before it fires.
            if (confirmationEnabled && !isConfirmation) {
                scheduleConfirmationAlarm()
            }
            animateVolume(0f, durationMs = 600)
            memoryGameView.postDelayed({ dismissAlarm() }, 600)
        }
    }

    override fun onCorrectTap(progress: Float) {
        // As the sequence is completed (progress 0→1), volume fades from roundBase down to ~10%
        val base = roundStartVolume(currentRound)
        val target = base * (1f - progress * 0.9f)
        animateVolume(target, durationMs = 400)
    }

    override fun onWrongAttempt(attemptCount: Int) {
        tvAttempts.text = "Attempts: $attemptCount"
        tvWrong.visibility = View.VISIBLE
        tvWrong.text = "WRONG!"
        tvWrong.alpha = 1f
        tvWrong.animate().alpha(0f).setDuration(1500).start()
        // Punishment: snap back to full volume
        snapVolume(1.0f)
    }

    override fun onSequenceShowStart() {
        tvStatus.text = "WATCH THE PATTERN"
        // Restore to round's base volume while showing the pattern
        animateVolume(roundStartVolume(currentRound), durationMs = 500)
    }

    override fun onSequenceShowEnd() {
        tvStatus.text = "YOUR TURN - TAP THE SEQUENCE"
    }

    private fun startGameCountdown() {
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                tvStatus.text = "WAKE UP! Game starts in ${seconds}s..."
            }
            override fun onFinish() {
                tvStatus.text = "GET READY..."
                memoryGameView.startGame()
            }
        }.start()
    }

    private fun snooze() {
        if (snoozeMinutes > 0) {
            // Cancel any existing confirmation — snooze replaces it, otherwise the
            // two alarms can fire at overlapping times and race each other.
            cancelConfirmationAlarm()
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
            // Bypass confirmation-scheduling — handled above. Just tear down.
            AlarmService.stop(this)
            timer.cancel()
            finishAndRemoveTask()
        }
    }

    private fun dismissAlarm() {
        AlarmService.stop(this)
        timer.cancel()
        finishAndRemoveTask()
    }

    private fun cancelConfirmationAlarm() {
        if (alarmId == -1L) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, com.alarmapp.alarmy.receiver.AlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, (alarmId + 20000).toInt(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleConfirmationAlarm() {
        if (alarmId == -1L) return
        // Replace any previously-pending confirmation before scheduling a new one.
        cancelConfirmationAlarm()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, com.alarmapp.alarmy.receiver.AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("is_confirmation", true)
        }
        // Use a unique request code (alarm_id + 20000) to avoid conflicts with snooze
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, (alarmId + 20000).toInt(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes
        val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(triggerTime, null)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        Toast.makeText(this, "Confirmation alarm in 5 minutes", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back button during alarm
    }

    override fun onDestroy() {
        timer.cancel()
        countdownTimer?.cancel()
        volumeAnimator?.cancel()
        super.onDestroy()
    }
}
