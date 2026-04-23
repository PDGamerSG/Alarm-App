package com.alarmapp.alarmy.data

import android.app.Application
import androidx.lifecycle.*
import com.alarmapp.alarmy.util.AlarmScheduler
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AlarmRepository
    val allAlarms: LiveData<List<Alarm>>

    init {
        val dao = AlarmDatabase.getDatabase(application).alarmDao()
        repository = AlarmRepository(dao)
        allAlarms = repository.allAlarms
    }

    fun insert(alarm: Alarm, callback: ((Long) -> Unit)? = null) = viewModelScope.launch {
        val id = repository.insert(alarm)
        val saved = alarm.copy(id = id)
        if (saved.isEnabled) {
            AlarmScheduler.schedule(getApplication(), saved)
        }
        callback?.invoke(id)
    }

    fun update(alarm: Alarm) = viewModelScope.launch {
        repository.update(alarm)
        AlarmScheduler.cancel(getApplication(), alarm)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), alarm)
        repository.delete(alarm)
    }

    fun toggleAlarm(alarm: Alarm) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        repository.update(updated)
        if (updated.isEnabled) {
            AlarmScheduler.schedule(getApplication(), updated)
        } else {
            AlarmScheduler.cancel(getApplication(), updated)
        }
    }

    suspend fun getAlarmById(id: Long): Alarm? = repository.getAlarmById(id)
}
