package com.huoyejia.ui

import android.Manifest
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huoyejia.R
import com.huoyejia.HuoyejiaViewModel
import com.huoyejia.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HuoyejiaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(viewModel.areNotificationsEnabled()) }
    
    // 避免状态循环
    val isProcessing = remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isProcessing.value = false
        if (isGranted) {
            viewModel.enableNotifications()
        } else {
            notificationsEnabled = false
        }
    }
    
    fun showTestNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channel = NotificationChannel(
            "test_channel",
            "测试通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        
        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(context, "test_channel")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("测试通知")
            .setContentText("这是测试通知")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 200, 200))
            .build()
        
        notificationManager.notify(999, notification)
    }
    
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("←")
                }
            }
        )
        
        Text("通知", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("每日回流提醒")
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (isProcessing.value) return@Switch
                    isProcessing.value = true
                    notificationsEnabled = enabled
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                            if (!alarmManager.canScheduleExactAlarms()) {
                                permissionLauncher.launch(Manifest.permission.SCHEDULE_EXACT_ALARM)
                                notificationsEnabled = false
                                isProcessing.value = false
                                return@Switch
                            }
                        }
                        viewModel.enableNotifications()
                    } else {
                        viewModel.disableNotifications()
                    }
                    isProcessing.value = false
                }
            )
        }
        Text("每天 21:13 自动推送回流卡片", style = MaterialTheme.typography.bodySmall)
        
        Button(
            onClick = { showTestNotification() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试通知")
        }
        
        Button(
            onClick = { requestOverlayPermission() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开启悬浮通知权限")
        }
    }
}