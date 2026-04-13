package com.alarmapp.alarmy.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.data.AlarmViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AddEditAlarmActivity : AppCompatActivity() {

    private lateinit var viewModel: AlarmViewModel
    private lateinit var timePicker: TimePicker
    private lateinit var etLabel: TextInputEditText
    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var tvRingtone: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var spinnerSnooze: Spinner
    private lateinit var spinnerDifficulty: Spinner

    private val dayButtons = mutableListOf<ToggleButton>()
    private var selectedRingtoneUri: String = ""
    private var editAlarmId: Long = -1L

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                selectedRingtoneUri = uri.toString()
                tvRingtone.text = RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Custom"
            } else {
                selectedRingtoneUri = ""
                tvRingtone.text = "Default"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_alarm)

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        timePicker = findViewById(R.id.timePicker)
        etLabel = findViewById(R.id.etAlarmLabel)
        switchVibrate = findViewById(R.id.switchVibrate)
        tvRingtone = findViewById(R.id.tvRingtone)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        spinnerSnooze = findViewById(R.id.spinnerSnooze)
        spinnerDifficulty = findViewById(R.id.spinnerDifficulty)

        timePicker.setIs24HourView(DateFormat.is24HourFormat(this))

        // Day toggle buttons
        val dayContainer = findViewById<LinearLayout>(R.id.dayContainer)
        for (i in 0 until dayContainer.childCount) {
            val child = dayContainer.getChildAt(i)
            if (child is ToggleButton) {
                dayButtons.add(child)
            }
        }

        // Snooze spinner
        val snoozeOptions = arrayOf("Disabled", "5 minutes", "10 minutes", "15 minutes")
        spinnerSnooze.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, snoozeOptions)

        // Difficulty spinner
        val difficultyOptions = arrayOf("Easy (3 blocks)", "Medium (5 blocks)", "Hard (7 blocks)")
        spinnerDifficulty.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, difficultyOptions)

        // Ringtone picker
        findViewById<LinearLayout>(R.id.ringtoneContainer).setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                if (selectedRingtoneUri.isNotBlank()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri))
                }
            }
            ringtonePickerLauncher.launch(intent)
        }

        btnSave.setOnClickListener { saveAlarm() }
        btnCancel.setOnClickListener { finish() }

        // Load existing alarm if editing
        editAlarmId = intent.getLongExtra("alarm_id", -1L)
        if (editAlarmId != -1L) {
            loadAlarm(editAlarmId)
        }
    }

    private fun loadAlarm(id: Long) {
        lifecycleScope.launch {
            val alarm = viewModel.getAlarmById(id) ?: return@launch

            timePicker.hour = alarm.hour
            timePicker.minute = alarm.minute
            etLabel.setText(alarm.label)
            switchVibrate.isChecked = alarm.vibrateEnabled

            if (alarm.ringtoneUri.isNotBlank()) {
                selectedRingtoneUri = alarm.ringtoneUri
                try {
                    val uri = Uri.parse(alarm.ringtoneUri)
                    tvRingtone.text = RingtoneManager.getRingtone(this@AddEditAlarmActivity, uri)?.getTitle(this@AddEditAlarmActivity) ?: "Custom"
                } catch (_: Exception) {
                    tvRingtone.text = "Custom"
                }
            }

            // Set day toggles
            for (i in dayButtons.indices) {
                if (i < Alarm.DAY_FLAGS.size) {
                    dayButtons[i].isChecked = alarm.isDayEnabled(Alarm.DAY_FLAGS[i])
                }
            }

            // Snooze
            spinnerSnooze.setSelection(
                when (alarm.snoozeMinutes) {
                    5 -> 1
                    10 -> 2
                    15 -> 3
                    else -> 0
                }
            )

            // Difficulty
            spinnerDifficulty.setSelection(
                when (alarm.difficulty) {
                    Alarm.DIFFICULTY_EASY -> 0
                    Alarm.DIFFICULTY_MEDIUM -> 1
                    Alarm.DIFFICULTY_HARD -> 2
                    else -> 0
                }
            )

            title = "Edit Alarm"
        }
    }

    private fun saveAlarm() {
        val hour = timePicker.hour
        val minute = timePicker.minute
        val label = etLabel.text?.toString() ?: ""
        val vibrate = switchVibrate.isChecked

        var repeatDays = 0
        for (i in dayButtons.indices) {
            if (i < Alarm.DAY_FLAGS.size && dayButtons[i].isChecked) {
                repeatDays = repeatDays or Alarm.DAY_FLAGS[i]
            }
        }

        val snoozeMinutes = when (spinnerSnooze.selectedItemPosition) {
            1 -> 5
            2 -> 10
            3 -> 15
            else -> 0
        }

        val difficulty = when (spinnerDifficulty.selectedItemPosition) {
            0 -> Alarm.DIFFICULTY_EASY
            1 -> Alarm.DIFFICULTY_MEDIUM
            2 -> Alarm.DIFFICULTY_HARD
            else -> Alarm.DIFFICULTY_EASY
        }

        val alarm = Alarm(
            id = if (editAlarmId != -1L) editAlarmId else 0,
            hour = hour,
            minute = minute,
            label = label,
            repeatDays = repeatDays,
            isEnabled = true,
            ringtoneUri = selectedRingtoneUri,
            vibrateEnabled = vibrate,
            snoozeMinutes = snoozeMinutes,
            difficulty = difficulty
        )

        if (editAlarmId != -1L) {
            viewModel.update(alarm)
        } else {
            viewModel.insert(alarm)
        }

        finish()
    }
}
