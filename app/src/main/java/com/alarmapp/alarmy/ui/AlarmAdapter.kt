package com.alarmapp.alarmy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alarmapp.alarmy.R
import com.alarmapp.alarmy.data.Alarm

class AlarmAdapter(
    private val onToggle: (Alarm) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    var use24Hour: Boolean = false

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvAlarmItemTime)
        val tvAmPm: TextView = view.findViewById(R.id.tvAlarmItemAmPm)
        val tvLabel: TextView = view.findViewById(R.id.tvAlarmItemLabel)
        val tvDays: TextView = view.findViewById(R.id.tvAlarmItemDays)
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

        holder.itemView.setOnClickListener { onClick(alarm) }
        holder.itemView.setOnLongClickListener {
            onLongClick(alarm)
            true
        }
    }
}

class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
    override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
}
