# 设置页提醒与通知改动总结

## 修改文件

- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt`
- `app/src/main/java/com/huoyejia/service/NotificationScheduler.kt`
- `app/src/main/java/com/huoyejia/service/DailyReviewAlarm.kt`
- `app/src/main/java/com/huoyejia/HuoyejiaViewModel.kt`
- `app/src/main/java/com/huoyejia/MainActivity.kt`

## 主要改动

- 设置页移除了左上角返回按钮和测试通知按钮，改为浅色柔光风格。
- 每日回流提醒支持用户自主开关，并支持设置提醒时间，默认时间为 `21:13`。
- 提醒开关和提醒时间会保存到本地 `SharedPreferences`，应用启动或手机重启后会按用户设置重新安排提醒。
- Android 13 及以上会先申请通知权限；Android 12 及以上如果没有精确闹钟权限，会引导用户进入系统设置。
- 横幅通知改为系统设置引导，提示 vivo 等机型需要用户手动打开顶部横幅、悬浮通知或顶部预览。
- 悬浮窗按钮改为“设置悬浮窗通知权限”，点击后进入系统悬浮窗权限设置页。
- 每日提醒通知被点击后，会直接回到回流箱页面。

## 验证方式

- 构建：运行 `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace`。
- 手动进入设置页，确认无返回按钮、无测试通知按钮。
- 开启每日回流提醒，退出应用后重新进入，确认开关状态保持。
- 修改提醒时间，重新进入设置页，确认新时间保持显示。
- 关闭每日回流提醒，确认不会继续安排新的提醒。
- 点击横幅通知权限入口，确认能进入系统通知设置或应用详情页。
- 点击悬浮窗通知权限入口，确认能进入系统悬浮窗权限页。
- 触发每日提醒通知后点击通知，确认直接进入回流箱。

## 已知系统限制

- 横幅通知、悬浮通知、顶部预览等开关属于手机厂商系统设置，应用不能在 vivo 等机型上直接替用户打开这些开关，只能创建高优先级通知渠道并引导用户手动开启。
- 精确闹钟权限也由系统控制；如果用户拒绝权限，应用不会误显示提醒已开启。
