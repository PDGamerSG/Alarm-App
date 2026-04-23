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
        AlarmScheduler.cancelPendingDisable(getApplication(), alarm)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
        }
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), alarm)
        AlarmScheduler.cancelPendingDisable(getApplication(), alarm)
        repository.delete(alarm)
    }

    fun toggleAlarm(alarm: Alarm) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled, pendingDisableAt = 0L)
        repository.update(updated)
        if (updated.isEnabled) {
            AlarmScheduler.schedule(getApplication(), updated)
        } else {
            AlarmScheduler.cancel(getApplication(), updated)
        }
        // Always clear any stale pending disable on a direct toggle
        AlarmScheduler.cancelPendingDisable(getApplication(), updated)
    }

    fun schedulePendingDisable(alarm: Alarm) = viewModelScope.launch {
        if (!alarm.isEnabled || !alarm.isProtected || alarm.pendingDisableAt > 0L) return@launch
        val triggerAt = System.currentTimeMillis() + Alarm.PROTECTED_DISABLE_DELAY_MS
        val updated = alarm.copy(pendingDisableAt = triggerAt)
        repository.update(updated)
        AlarmScheduler.schedulePendingDisable(getApplication(), updated, triggerAt)
    }

    fun cancelPendingDisable(alarm: Alarm) = viewModelScope.launch {
        if (alarm.pendingDisableAt == 0L) return@launch
        val updated = alarm.copy(pendingDisableAt = 0L)
        repository.update(updated)
        AlarmScheduler.cancelPendingDisable(getApplication(), updated)
    }

    suspend fun getAlarmById(id: Long): Alarm? = repository.getAlarmById(id)
}
