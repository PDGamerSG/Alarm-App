package com.alarmapp.alarmy.util

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alarmapp.alarmy.receiver.AlarmDeviceAdminReceiver

object PermissionHelper {

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryOptimizationExemption(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun isDeviceAdminEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, AlarmDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    fun requestDeviceAdmin(context: Context): Intent {
        val componentName = ComponentName(context, AlarmDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Alarmy needs Device Admin to prevent force-stopping and ensure alarms fire reliably."
            )
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun requestExactAlarmPermission(): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    data class PermissionStatus(
        val batteryOptimization: Boolean,
        val deviceAdmin: Boolean,
        val exactAlarm: Boolean,
        val notification: Boolean
    ) {
        val allGranted: Boolean
            get() = batteryOptimization && deviceAdmin && exactAlarm && notification

        val missingCount: Int
            get() = listOf(batteryOptimization, deviceAdmin, exactAlarm, notification)
                .count { !it }
    }

    fun checkAll(context: Context): PermissionStatus {
        return PermissionStatus(
            batteryOptimization = isBatteryOptimizationExempt(context),
            deviceAdmin = isDeviceAdminEnabled(context),
            exactAlarm = canScheduleExactAlarms(context),
            notification = hasNotificationPermission(context)
        )
    }
}
