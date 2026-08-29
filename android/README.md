# MeloX Android 开发目录

这里是 MeloX Android 的原生 Android 工程。

当前开发版本：`0.5.2`（`versionCode 16`）。

完整的项目介绍、功能状态、许可证、第三方项目与构建说明请参阅仓库根目录的 [`README.md`](../README.md)。

## 设计目标

- 尽可能保留 MeloX SwiftUI 版本的视觉语言与交互逻辑；
- Android 客户端保持原生实现：Kotlin + Jetpack Compose；
- 使用 Android 平台媒体能力替代 Apple 私有或平台专属 API；
- HyperOS 增强能力保持可选，普通 Android 设备仍可正常使用；
- Root 权限不是应用正常运行的必要条件。

## 当前技术栈

- Kotlin
- Jetpack Compose
- Navigation Compose
- AndroidX Media3 / ExoPlayer
- MediaSessionService
- Coil
- OkHttp
- Kotlin Coroutines
- Miuix `miuix-blur`（实验性玻璃 / Backdrop）
- HyperOS 焦点通知桥接（可选）

## 构建

需要：

- JDK 17
- Android SDK 37
- Gradle 9.5.0

在当前目录运行：

```bash
gradle :app:assembleDebug --stacktrace
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```
