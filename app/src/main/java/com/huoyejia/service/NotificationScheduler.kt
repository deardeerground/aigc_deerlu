package com.huoyejia.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReminderTime(
    val hour: Int,
    val minute: Int
) {
    fun formatted(): String = "%02d:%02d".format(hour, minute)
}

class NotificationScheduler(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_NOTIFICATION_ENABLED = "reminder_enabled"
        private const val LEGACY_KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_LAST_SCHEDULED = "last_scheduled"
        private const val DEFAULT_REMINDER_HOUR = 21
        private const val DEFAULT_REMINDER_MINUTE = 13
    }
    
    private val _isEnabled = MutableStateFlow(
        prefs.getBoolean(
            KEY_NOTIFICATION_ENABLED,
            prefs.getBoolean(LEGACY_KEY_NOTIFICATION_ENABLED, false)
        )
    )
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _reminderTime = MutableStateFlow(
        ReminderTime(
            hour = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR),
            minute = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        )
    )
    val reminderTime: StateFlow<ReminderTime> = _reminderTime.asStateFlow()
    
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
    
    fun enableNotifications() {
        _isEnabled.value = true
        prefs.edit()
            .putBoolean(KEY_NOTIFICATION_ENABLED, true)
            .remove(LEGACY_KEY_NOTIFICATION_ENABLED)
            .apply()
        scheduleNextReminder()
    }
    
    fun disableNotifications() {
        _isEnabled.value = false
        prefs.edit()
            .putBoolean(KEY_NOTIFICATION_ENABLED, false)
            .remove(LEGACY_KEY_NOTIFICATION_ENABLED)
            .apply()
        DailyReviewAlarm.cancelWork(context)
    }
    
    fun refreshState() {
        val newValue = prefs.getBoolean(
            KEY_NOTIFICATION_ENABLED,
            prefs.getBoolean(LEGACY_KEY_NOTIFICATION_ENABLED, false)
        )
        if (_isEnabled.value != newValue) {
            _isEnabled.value = newValue
        }
        refreshReminderTime()
    }
    
    fun areNotificationsEnabled(): Boolean {
        return _isEnabled.value
    }
    
    fun updateLastScheduledTime() {
        prefs.edit().putLong(KEY_LAST_SCHEDULED, System.currentTimeMillis()).apply()
    }
    
    fun getLastScheduledTime(): Long {
        return prefs.getLong(KEY_LAST_SCHEDULED, 0L)
    }
    
    fun scheduleIfEnabled() {
        if (areNotificationsEnabled()) {
            scheduleNextReminder()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val normalized = ReminderTime(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59)
        )
        _reminderTime.value = normalized
        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, normalized.hour)
            .putInt(KEY_REMINDER_MINUTE, normalized.minute)
            .apply()
        if (areNotificationsEnabled()) {
            DailyReviewAlarm.cancelWork(context)
            scheduleNextReminder()
        }
    }

    private fun refreshReminderTime() {
        val next = ReminderTime(
            hour = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR),
            minute = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        )
        if (_reminderTime.value != next) {
            _reminderTime.value = next
        }
    }

    private fun scheduleNextReminder() {
        val time = _reminderTime.value
        DailyReviewAlarm.scheduleWork(context, time.hour, time.minute)
        updateLastScheduledTime()
    }
    
    fun requestExactAlarmPermission(): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }
}
