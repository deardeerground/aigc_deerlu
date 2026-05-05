package com.huoyejia.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.huoyejia.HuoyejiaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationScheduler(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_LAST_SCHEDULED = "last_scheduled"
    }
    
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
    
    fun enableNotifications() {
        _isEnabled.value = true
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply()
        DailyReviewAlarm.scheduleWork(context)
    }
    
    fun disableNotifications() {
        _isEnabled.value = false
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, false).apply()
        DailyReviewAlarm.cancelWork(context)
    }
    
    fun refreshState() {
        val newValue = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        if (_isEnabled.value != newValue) {
            _isEnabled.value = newValue
        }
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
            DailyReviewAlarm.scheduleWork(context)
        }
    }
    
    fun requestExactAlarmPermission(): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }
}