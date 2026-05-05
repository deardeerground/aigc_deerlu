package com.huoyejia.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
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
import androidx.compose.ui.window.Dialog
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
    // 使用 StateFlow 来观察通知状态
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    
    // 避免状态循环
    val isProcessing = remember { mutableStateOf(false) }
    
    // 权限请求 Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isProcessing.value = false
    }
    
    // 引导对话框状态
    var showBannerGuide by remember { mutableStateOf(false) }
    
    // 横幅通知状态提示
    var showBannerTip by remember { mutableStateOf(false) }
    
    fun showTestNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 清除之前的通知
        notificationManager.cancel(999)
        
        // 确保通道存在且设置正确
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "test_channel",
                "测试通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于测试通知横幅和锁屏显示"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                setVibrationPattern(longArrayOf(0, 200, 100, 200))
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                importance = NotificationManager.IMPORTANCE_HIGH
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, "test_channel")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("测试通知")
            .setContentText("横幅和锁屏都能显示此通知")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setOngoing(false)
            .setSound(null)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("这是一个测试通知。\n横幅显示：✓\n锁屏显示：✓\n请点击关闭。"))
        
        val notification = builder.build()
        notificationManager.notify(999, notification)
    }
    
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                val intent = Intent(
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("每日回流提醒")
                Text(
                    "横幅通知需手动开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (isProcessing.value) return@Switch
                        
                        isProcessing.value = true
                        
                        when {
                            enabled -> {
                                // 开启通知
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                                    if (!alarmManager.canScheduleExactAlarms()) {
                                        // 没有权限，跳转到设置页请求精确闹钟权限
                                        val intent = viewModel.requestExactAlarmPermission()
                                        context.startActivity(intent)
                                        isProcessing.value = false
                                        return@Switch
                                    }
                                }
                                // 有权限，启用通知
                                viewModel.enableNotifications()
                            }
                            else -> {
                                // 关闭通知
                                viewModel.disableNotifications()
                            }
                        }
                        
                        // 操作完成后刷新状态
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            viewModel.refreshNotificationState()
                            isProcessing.value = false
                        }, 100)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("开启横幅", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("每天 21:13 自动推送回流卡片", style = MaterialTheme.typography.bodySmall)
        
        // 测试通知按钮，添加 loading 状态
        var isTesting by remember { mutableStateOf(false) }
        Button(
            onClick = {
                if (!isTesting) {
                    isTesting = true
                    viewModel.testNotification()
                    // 延迟重置状态，避免用户疯狂点击
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isTesting = false
                    }, 1000)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            if (isTesting) {
                Text("测试中...")
            } else {
                Text("测试通知")
            }
        }
        
        // 横幅通知状态提示按钮
        Button(
            onClick = { showBannerTip = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("横幅通知未开启？")
        }
        
        // 横幅通知引导按钮
        Button(
            onClick = { showBannerGuide = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("设置横幅通知")
        }
        
        Button(
            onClick = { requestOverlayPermission() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开启悬浮通知权限")
        }
    }
    
    // 横幅通知引导对话框
    if (showBannerGuide) {
        Dialog(onDismissRequest = { showBannerGuide = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "设置横幅通知",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "为了在手机顶端显示通知，请按以下步骤操作：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GuideItem(number = "1", text = "点击下方按钮打开系统通知设置")
                        GuideItem(number = "2", text = "找到「aigc_deerlu」应用")
                        GuideItem(number = "3", text = "确保通知已开启")
                        GuideItem(number = "4", text = "开启「横幅通知」选项")
                        GuideItem(number = "5", text = "点击返回即可")
                    }
                    
                    Button(
                        onClick = {
                            // 打开系统通知设置
                            val intent = Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            ).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            showBannerGuide = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开系统设置")
                    }
                    
                    TextButton(
                        onClick = { showBannerGuide = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("稍后再说")
                    }
                }
            }
        }
    }
    
    // 横幅通知状态提示对话框
    if (showBannerTip) {
        Dialog(onDismissRequest = { showBannerTip = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "横幅通知未开启",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "您的手机横幅通知默认关闭。开启后，通知将在手机顶端以横幅形式显示，更加醒目。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Button(
                        onClick = {
                            // 打开系统通知设置
                            val intent = Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            ).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            showBannerTip = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("去设置横幅通知")
                    }
                    
                    TextButton(
                        onClick = { showBannerTip = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun GuideItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "• $number",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}