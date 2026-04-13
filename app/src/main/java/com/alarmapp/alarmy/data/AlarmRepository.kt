package com.alarmapp.alarmy.data

import androidx.lifecycle.LiveData

class AlarmRepository(private val alarmDao: AlarmDao) {
    val allAlarms: LiveData<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun insert(alarm: Alarm): Long = alarmDao.insert(alarm)

    suspend fun update(alarm: Alarm) = alarmDao.update(alarm)

    suspend fun delete(alarm: Alarm) = alarmDao.delete(alarm)

    suspend fun deleteById(id: Long) = alarmDao.deleteById(id)

    suspend fun getAlarmById(id: Long): Alarm? = alarmDao.getAlarmById(id)

    suspend fun getEnabledAlarms(): List<Alarm> = alarmDao.getEnabledAlarms()

    suspend fun getAllAlarmsList(): List<Alarm> = alarmDao.getAllAlarmsList()
}
