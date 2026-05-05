package com.huoyejia.service

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.huoyejia.HuoyejiaApp

class NotificationScheduler(private val context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_LAST_SCHEDULED = "last_scheduled"
    }
    
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
    
    fun enableNotifications() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, true).apply()
        DailyReviewAlarm.scheduleWork(context)
    }
    
    fun disableNotifications() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, false).apply()
        DailyReviewAlarm.cancelWork(context)
    }
    
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
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
}