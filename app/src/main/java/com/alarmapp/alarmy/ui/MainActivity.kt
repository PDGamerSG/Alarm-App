package com.alarmapp.alarmy.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.AlarmViewModel
import com.alarmapp.alarmy.util.AlarmScheduler
import com.alarmapp.alarmy.util.PermissionHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter
    private lateinit var warningBanner: LinearLayout
    private lateinit var tvWarning: TextView
    private lateinit var tvEmpty: TextView

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            adapter.refreshTimeLeft()
            val delay = if (adapter.hasActivePendingDisable()) 1_000L else 60_000L
            tickHandler.postDelayed(this, delay)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionWarning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        warningBanner = findViewById(R.id.warningBanner)
        tvWarning = findViewById(R.id.tvWarning)
        tvEmpty = findViewById(R.id.tvEmpty)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerAlarms)
        adapter = AlarmAdapter(
            onToggle = { alarm ->
                val pendingActive = alarm.pendingDisableAt > System.currentTimeMillis()
                when {
                    // Tapping switch while pending → cancel the pending disable
                    pendingActive -> {
                        viewModel.cancelPendingDisable(alarm)
                        Toast.makeText(this, "Pending disable cancelled", Toast.LENGTH_SHORT).show()
                    }
                    // Disabling a protected enabled alarm → schedule delayed disable
                    alarm.isEnabled && alarm.isProtected -> {
                        viewModel.schedulePendingDisable(alarm)
                        Toast.makeText(
                            this,
                            "Protected alarm — disabling in 10 minutes. Tap Cancel to reverse.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> viewModel.toggleAlarm(alarm)
                }
            },
            onClick = { alarm ->
                val intent = Intent(this, AddEditAlarmActivity::class.java)
                intent.putExtra("alarm_id", alarm.id)
                startActivity(intent)
            },
            onLongClick = { alarm ->
                val label = alarm.label.ifBlank { "Alarm" }
                val options = arrayOf("Copy Alarm", "Delete Alarm")
                AlertDialog.Builder(this)
                    .setTitle(label)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                val copy = alarm.copy(
                                    id = 0,
                                    label = if (alarm.label.isNotBlank()) "${alarm.label} (Copy)" else "",
                                    isEnabled = true,
                                    pendingDisableAt = 0L
                                )
                                viewModel.insert(copy)
                            }
                            1 -> {
                                if (alarm.isProtected && alarm.isEnabled) {
                                    Toast.makeText(
                                        this,
                                        "Protected alarm — disable it first (10-min delay).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    AlertDialog.Builder(this)
                                        .setTitle("Delete Alarm")
                                        .setMessage("Delete \"$label\"?")
                                        .setPositiveButton("Delete") { _, _ -> viewModel.delete(alarm) }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            }
                        }
                    }
                    .show()
            },
            onCancelPendingDisable = { alarm ->
                viewModel.cancelPendingDisable(alarm)
                Toast.makeText(this, "Pending disable cancelled", Toast.LENGTH_SHORT).show()
            }
        )
        adapter.use24Hour = DateFormat.is24HourFormat(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null

        var previousIds: Set<Long> = emptySet()
        viewModel.allAlarms.observe(this) { alarms ->
            val sorted = alarms.sortedWith(
                compareByDescending<com.alarmapp.alarmy.data.Alarm> { it.isEnabled }
                    .thenBy {
                        if (it.isEnabled) AlarmScheduler.getNextTriggerTime(it) else Long.MAX_VALUE
                    }
                    .thenBy { it.hour }
                    .thenBy { it.minute }
            )
            val currentIds = sorted.map { it.id }.toSet()
            val addedAlarm = (currentIds - previousIds).isNotEmpty() && previousIds.isNotEmpty()
            val isFirstLoad = previousIds.isEmpty() && sorted.isNotEmpty()
            previousIds = currentIds

            adapter.submitList(sorted) {
                if (addedAlarm || isFirstLoad) {
                    recyclerView.scrollToPosition(0)
                }
                // Re-pace tick based on whether any pending countdown is active
                tickHandler.removeCallbacks(tickRunnable)
                val delay = if (adapter.hasActivePendingDisable()) 1_000L else millisUntilNextMinute()
                tickHandler.postDelayed(tickRunnable, delay)
            }
            tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        }

        val fab = findViewById<FloatingActionButton>(R.id.fabAddAlarm)
        fab.setOnClickListener {
            startActivity(Intent(this, AddEditAlarmActivity::class.java))
        }

        warningBanner.setOnClickListener {
            requestMissingPermissions()
        }

        // First launch permission check
        if (savedInstanceState == null) {
            checkFirstLaunchPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionWarning()
        adapter.use24Hour = DateFormat.is24HourFormat(this)
        adapter.notifyDataSetChanged()

        tickHandler.removeCallbacks(tickRunnable)
        val initialDelay = if (adapter.hasActivePendingDisable()) 1_000L else millisUntilNextMinute()
        tickHandler.postDelayed(tickRunnable, initialDelay)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
    }

    private fun millisUntilNextMinute(): Long {
        val now = System.currentTimeMillis()
        return 60_000L - (now % 60_000L)
    }

    private fun updatePermissionWarning() {
        val status = PermissionHelper.checkAll(this)
        if (status.allGranted) {
            warningBanner.visibility = View.GONE
        } else {
            warningBanner.visibility = View.VISIBLE
            tvWarning.text = "${status.missingCount} permission(s) needed for reliable alarms. Tap to fix."
        }
    }

    private fun checkFirstLaunchPermissions() {
        val prefs = getSharedPreferences("alarmy_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("first_launch_done", false)) {
            prefs.edit().putBoolean("first_launch_done", true).apply()
            requestMissingPermissions()
        }
    }

    private fun requestMissingPermissions() {
        val status = PermissionHelper.checkAll(this)

        if (!status.notification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!status.exactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(PermissionHelper.requestExactAlarmPermission())
            } catch (_: Exception) {
            }
        }

        if (!status.batteryOptimization) {
            try {
                startActivity(PermissionHelper.requestBatteryOptimizationExemption(this))
            } catch (_: Exception) {
            }
        }

        if (!status.deviceAdmin) {
            try {
                startActivity(PermissionHelper.requestDeviceAdmin(this))
            } catch (_: Exception) {
            }
        }
    }
}
