package com.huoyejia.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huoyejia.HuoyejiaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HuoyejiaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val reminderTime by viewModel.reminderTime.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showBannerGuide by remember { mutableStateOf(false) }
    var showExactAlarmGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationState()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && canScheduleExactAlarm(context)) {
            viewModel.enableNotifications()
        } else if (granted) {
            showExactAlarmGuide = true
        }
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun requestEnableReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (!canScheduleExactAlarm(context)) {
            showExactAlarmGuide = true
            return
        }

        viewModel.enableNotifications()
    }

    TechBackground(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B2638)
                )
                Text(
                    text = "管理每日回流提醒和系统通知权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF667085)
                )
            }

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "每日回流提醒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D2939)
                        )
                        Text(
                            text = if (notificationsEnabled) {
                                "已开启，每天 ${reminderTime.formatted()} 推送提醒"
                            } else {
                                "关闭后不会安排新的定时提醒"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF667085)
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) requestEnableReminder() else viewModel.disableNotifications()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF8FBFF))
                        .border(1.dp, Color(0xFFE1EAF4), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "提醒时间",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF344054)
                        )
                        Text(
                            text = reminderTime.formatted(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1570EF)
                        )
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text("设置时间")
                    }
                }
            }

            SettingsCard {
                SettingsSectionTitle("系统通知")
                Text(
                    text = "部分手机不会允许应用直接打开顶部横幅。vivo 等机型通常需要进入系统通知设置，手动开启横幅通知、悬浮通知或顶部预览。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF667085)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showBannerGuide = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("设置横幅通知权限")
                }
            }

            SettingsCard {
                SettingsSectionTitle("悬浮窗通知")
                Text(
                    text = "用于采集悬浮窗等浮层能力。点击后会进入系统权限页，请为活页夹打开悬浮窗权限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF667085)
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { openOverlaySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("设置悬浮窗通知权限")
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderTime.hour,
            initialMinute = reminderTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("设置提醒时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setReminderTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBannerGuide) {
        AlertDialog(
            onDismissRequest = { showBannerGuide = false },
            title = { Text("设置横幅通知权限") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuideItem("1", "在系统设置中打开“通知”或“通知管理”。")
                    GuideItem("2", "找到活页夹，确认允许通知。")
                    GuideItem("3", "手动开启“横幅通知”“悬浮通知”或“顶部预览”。")
                    GuideItem("4", "vivo 等机型可能在“通知显示方式”里配置顶部横幅。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openNotificationSettings(context)
                        showBannerGuide = false
                    }
                ) {
                    Text("去系统设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBannerGuide = false }) {
                    Text("稍后再说")
                }
            }
        )
    }

    if (showExactAlarmGuide) {
        AlertDialog(
            onDismissRequest = { showExactAlarmGuide = false },
            title = { Text("需要定时提醒权限") },
            text = {
                Text("为了按你设置的时间准时提醒，请在系统页面允许活页夹设置精确闹钟，然后回到应用重新开启每日回流提醒。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(viewModel.requestExactAlarmPermission())
                        showExactAlarmGuide = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmGuide = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFECEFF5), RoundedCornerShape(24.dp))
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1D2939)
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun GuideItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6F7F5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF027A7A)
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475467)
        )
    }
}

private fun canScheduleExactAlarm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }
    return true
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
