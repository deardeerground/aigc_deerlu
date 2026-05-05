package com.huoyejia

import android.app.Application
import com.huoyejia.service.NotificationScheduler

class HuoyejiaApp : Application() {
    lateinit var container: AppContainer
        private set
    
    companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        
        val notificationScheduler = NotificationScheduler(this)
        notificationScheduler.scheduleIfEnabled()
    }
}