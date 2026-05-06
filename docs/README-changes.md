# 改动说明 README

## 本次改动概览

这次主要完成了两部分工作：

1. 完善设置页的定时提醒、通知权限和跳转逻辑。
2. 将应用整体 UI 调整为浅色、柔和、科技感风格，点缀浅蓝色和浅紫色渐变，并增加轻微动态背景效果。

## 设置页与通知能力

修改文件：

- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt`
- `app/src/main/java/com/huoyejia/service/NotificationScheduler.kt`
- `app/src/main/java/com/huoyejia/service/DailyReviewAlarm.kt`
- `app/src/main/java/com/huoyejia/HuoyejiaViewModel.kt`
- `app/src/main/java/com/huoyejia/MainActivity.kt`

主要改动：

- 设置页删除了左上角返回按钮。
- 删除了测试通知按钮。
- 新增每日回流提醒开关，用户可以自主开启或关闭。
- 新增提醒时间设置，默认时间为 `21:13`。
- 提醒开关和提醒时间会持久化保存。
- 开启提醒时会检查通知权限和精确闹钟权限。
- Android 13 及以上会引导申请通知权限。
- Android 12 及以上如果没有精确闹钟权限，会引导用户进入系统设置。
- “开启悬浮窗通知权限”改为“设置悬浮窗通知权限”。
- 横幅通知改为系统设置引导，提示 vivo 等机型需要手动开启横幅通知、悬浮通知或顶部预览。
- 点击每日提醒通知后，会直接跳转回回流箱页面。

## 全局 UI 风格改造

修改文件：

- `app/src/main/java/com/huoyejia/ui/theme/Theme.kt`
- `app/src/main/java/com/huoyejia/ui/TechStyle.kt`
- `app/src/main/java/com/huoyejia/ui/AppScaffold.kt`
- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt`
- `app/src/main/java/com/huoyejia/ui/CollectionListScreen.kt`
- `app/src/main/java/com/huoyejia/ui/CollectionDetailScreen.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`

主要改动：

- 全局主题从原来的偏暖色调，改为浅蓝、浅紫、青色为主的科技感浅色主题。
- 新增 `TechStyle.kt`，集中管理科技风视觉组件：
  - `TechBackground`：浅色动态柔光背景。
  - `TechPrimaryGradient`：蓝紫青渐变。
  - `techCardColors`：白色半透明卡片色。
  - `techPanelBorder`：浅蓝边框。
- 底部导航改为白色半透明玻璃感，并使用浅蓝选中态。
- 忙碌提示条改为蓝紫渐变。
- 设置页使用统一的浅色柔光背景。
- 回流箱列表页改为透明 Scaffold，使用动态背景和白色玻璃感卡片。
- 收藏夹详情页改为浅色顶部栏、动态背景、白色卡片。
- 首页 Hero 卡片改为蓝紫青渐变。
- 通用笔记卡、复习卡、辅助信息卡等改为白色半透明卡片和浅蓝边框。
- 重复收藏提示等旧的黄橙色块改为浅紫体系，减少突兀感。
- 底部导航中文文案修正为：回流箱、采集、复习、指数、设置。

## 新增文档

- `docs/settings-notification-changes.md`
  - 专门总结设置页提醒和通知相关改动。
- `docs/README-changes.md`
  - 当前这份总览文档，汇总设置、通知和 UI 风格改造。

## 构建验证

已运行并通过：

```powershell
$env:ANDROID_HOME='D:\androidstudiosdk'
$env:ANDROID_SDK_ROOT='D:\androidstudiosdk'
$env:GRADLE_USER_HOME='E:\aigc_zly\.gradle'
.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace
```

构建结果：

- 构建成功。
- 仅剩一个原有 warning：`CollectionListScreen.kt` 中有一个条件始终为 true，不影响 APK 生成。

## APK 位置

最新 APK：

```text
E:\aigc_0505\aigc_deerlu\app\build\outputs\apk\debug\app-debug.apk
```

生成时间：

```text
2026/5/5 17:39
```

## 已知说明

- 横幅通知属于系统和厂商权限能力，vivo 等机型通常不允许应用直接替用户打开顶部横幅，只能跳转系统设置并提供引导。
- 动态背景是轻量 Compose 动画，不依赖新增第三方库。
- 当前改造优先保证全局风格统一和构建稳定，部分业务文案仍保留原文件中的历史乱码内容，后续可以单独做一次中文文案清理。
