package com.alarmapp.alarmy.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val repeatDays: Int = 0, // bitmask: bit 0=Mon, bit 1=Tue, ..., bit 6=Sun
    val isEnabled: Boolean = true,
    val ringtoneUri: String = "",
    val vibrateEnabled: Boolean = true,
    val snoozeMinutes: Int = 5, // 0 = disabled, 5, 10, 15
    val difficulty: Int = DIFFICULTY_EASY
) {
    companion object {
        const val DIFFICULTY_EASY = 3
        const val DIFFICULTY_MEDIUM = 5
        const val DIFFICULTY_HARD = 7

        const val MON = 0x01
        const val TUE = 0x02
        const val WED = 0x04
        const val THU = 0x08
        const val FRI = 0x10
        const val SAT = 0x20
        const val SUN = 0x40

        val DAY_FLAGS = intArrayOf(MON, TUE, WED, THU, FRI, SAT, SUN)
        val DAY_NAMES = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }

    fun isDayEnabled(dayFlag: Int): Boolean = (repeatDays and dayFlag) != 0

    fun isRepeating(): Boolean = repeatDays != 0

    fun getRepeatDaysText(): String {
        if (repeatDays == 0) return "Once"
        if (repeatDays == 0x7F) return "Every day"
        if (repeatDays == 0x1F) return "Weekdays"
        if (repeatDays == 0x60) return "Weekends"
        return DAY_FLAGS.indices
            .filter { isDayEnabled(DAY_FLAGS[it]) }
            .joinToString(", ") { DAY_NAMES[it] }
    }

    fun getFormattedTime(use24Hour: Boolean): String {
        return if (use24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format("%d:%02d %s", h, minute, amPm)
        }
    }
}
