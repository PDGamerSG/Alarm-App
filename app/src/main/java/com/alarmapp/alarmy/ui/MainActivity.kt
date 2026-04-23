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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.AlarmViewModel
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
            tickHandler.postDelayed(this, 60_000L)
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
            onToggle = { alarm -> viewModel.toggleAlarm(alarm) },
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
                                // Copy: duplicate with new ID, enabled by default
                                val copy = alarm.copy(
                                    id = 0,
                                    label = if (alarm.label.isNotBlank()) "${alarm.label} (Copy)" else "",
                                    isEnabled = true
                                )
                                viewModel.insert(copy)
                            }
                            1 -> {
                                AlertDialog.Builder(this)
                                    .setTitle("Delete Alarm")
                                    .setMessage("Delete \"$label\"?")
                                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(alarm) }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .show()
            }
        )
        adapter.use24Hour = DateFormat.is24HourFormat(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.allAlarms.observe(this) { alarms ->
            adapter.submitList(alarms)
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
        tickHandler.postDelayed(tickRunnable, millisUntilNextMinute())
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
