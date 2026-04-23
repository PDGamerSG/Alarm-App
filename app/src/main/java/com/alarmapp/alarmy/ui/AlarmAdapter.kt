package com.alarmapp.alarmy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm
import com.alarmapp.alarmy.util.AlarmScheduler

class AlarmAdapter(
    private val onToggle: (Alarm) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit,
    private val onCancelPendingDisable: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    var use24Hour: Boolean = false

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvAlarmItemTime)
        val tvAmPm: TextView = view.findViewById(R.id.tvAlarmItemAmPm)
        val tvLabel: TextView = view.findViewById(R.id.tvAlarmItemLabel)
        val tvDays: TextView = view.findViewById(R.id.tvAlarmItemDays)
        val tvTimeLeft: TextView = view.findViewById(R.id.tvAlarmItemTimeLeft)
        val pendingContainer: LinearLayout = view.findViewById(R.id.pendingDisableContainer)
        val tvPending: TextView = view.findViewById(R.id.tvPendingDisable)
        val btnCancelPending: TextView = view.findViewById(R.id.btnCancelPendingDisable)
        val switchEnabled: SwitchCompat = view.findViewById(R.id.switchAlarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = getItem(position)

        if (use24Hour) {
            holder.tvTime.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
            holder.tvAmPm.visibility = View.GONE
        } else {
            val h = if (alarm.hour == 0) 12 else if (alarm.hour > 12) alarm.hour - 12 else alarm.hour
            holder.tvTime.text = String.format("%d:%02d", h, alarm.minute)
            holder.tvAmPm.text = if (alarm.hour < 12) "AM" else "PM"
            holder.tvAmPm.visibility = View.VISIBLE
        }

        holder.tvLabel.text = alarm.label.ifBlank { "Alarm" }
        holder.tvDays.text = alarm.getRepeatDaysText()

        val now = System.currentTimeMillis()
        val pendingActive = alarm.isEnabled && alarm.pendingDisableAt > now

        if (alarm.isEnabled) {
            val triggerTime = AlarmScheduler.getNextTriggerTime(alarm)
            holder.tvTimeLeft.text = formatTimeLeft(triggerTime - now)
            holder.tvTimeLeft.visibility = View.VISIBLE
        } else {
            holder.tvTimeLeft.visibility = View.GONE
        }

        if (pendingActive) {
            holder.pendingContainer.visibility = View.VISIBLE
            holder.tvPending.text = "Disabling in ${formatShortDuration(alarm.pendingDisableAt - now)}"
            holder.btnCancelPending.setOnClickListener { onCancelPendingDisable(alarm) }
        } else {
            holder.pendingContainer.visibility = View.GONE
            holder.btnCancelPending.setOnClickListener(null)
        }

        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = alarm.isEnabled
        holder.switchEnabled.setOnCheckedChangeListener { _, _ ->
            onToggle(alarm)
        }

        val alpha = if (alarm.isEnabled) 1.0f else 0.5f
        holder.tvTime.alpha = alpha
        holder.tvAmPm.alpha = alpha
        holder.tvLabel.alpha = alpha
        holder.tvDays.alpha = alpha
        holder.tvTimeLeft.alpha = alpha

        holder.itemView.setOnClickListener { onClick(alarm) }
        holder.itemView.setOnLongClickListener {
            onLongClick(alarm)
            true
        }
    }

    fun refreshTimeLeft() {
        notifyItemRangeChanged(0, itemCount, PAYLOAD_TIME_LEFT)
    }

    fun hasActivePendingDisable(): Boolean {
        val now = System.currentTimeMillis()
        for (i in 0 until itemCount) {
            val a = getItem(i)
            if (a.isEnabled && a.pendingDisableAt > now) return true
        }
        return false
    }

    companion object {
        private const val PAYLOAD_TIME_LEFT = "time_left"

        private fun formatShortDuration(millis: Long): String {
            if (millis <= 0L) return "0s"
            val totalSeconds = (millis + 999L) / 1000L // round up
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        }

        private fun formatTimeLeft(millis: Long): String {
            if (millis <= 0L) return "Ringing soon"
            val totalMinutes = (millis / 60_000L) + 1 // round up
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val minutes = totalMinutes % 60

            val parts = buildString {
                append("Rings in ")
                when {
                    days > 0 -> {
                        append("${days}d")
                        if (hours > 0) append(" ${hours}h")
                    }
                    hours > 0 -> {
                        append("${hours}h")
                        if (minutes > 0) append(" ${minutes}m")
                    }
                    minutes > 1 -> append("${minutes}m")
                    else -> append("less than 1m")
                }
            }
            return parts
        }
    }
}

class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
    override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
}
