package com.alarmapp.alarmy

import android.app.Application
import com.alarmapp.alarmy.util.AlarmScheduler

class AlarmyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmScheduler.createNotificationChannels(this)
    }
}
