# 弥乐

[English](README.md)

[![Downloads](https://img.shields.io/github/downloads/lladlam/MeloX-Android/total?label=downloads&color=2ea44f)](https://github.com/lladlam/MeloX-Android/releases)
[![Release](https://img.shields.io/github/v/release/lladlam/MeloX-Android?display_name=release&label=release&color=ff2d55)](https://github.com/lladlam/MeloX-Android/releases/latest)
[![Last Commit](https://img.shields.io/github/last-commit/lladlam/MeloX-Android/main?label=last%20commit&color=007aff)](https://github.com/lladlam/MeloX-Android/commits/main)
[![QQ群](https://img.shields.io/badge/QQ%E7%BE%A4-MeloX--Android-12B7F5?logo=tencentqq&logoColor=white)](https://qm.qq.com/q/wbhFQxj7mo)
[![Telegram](https://img.shields.io/badge/Telegram-MeloX--Android-26A5E4?logo=telegram&logoColor=white)](https://t.me/+Eiy42NxIVNUwOWU1)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

<p align="center">
  <img src="docs/icon.png" width="128" alt="弥乐图标" />
</p>

<p align="center">
  使用 Kotlin + Jetpack Compose 构建的弥乐原生 Android 迁移版
</p>

## 桌面版

**Windows** 与 **Linux** 版本已处于最终验证阶段。想使用 Windows 版本的朋友，欢迎 Star 这两个项目：

- [MeloX-Windows](https://github.com/lladlam/MeloX-Windows)
- [MeloX-Linux](https://github.com/lladlam/MeloX-Linux)

> [!IMPORTANT]
> **弥乐仍处于开发阶段。** 已完成当前 Android 平台范围内的核心功能迁移，并持续以弥乐主线作为行为基准；第三方接口、OEM 协议和不同系统版本仍可能带来兼容性变化。

> 弥乐是非官方开源项目，与网易云音乐、小米、Apple 及其关联公司不存在隶属、合作或授权关系。

## 当前版本：0.7.2

`0.7.2`（`versionCode 27`，2026-11-06）新增实验性插件音源（Plugin API v1）与可选 MeloX Rear 副屏伴侣入口，并完善后台页面恢复、首页快捷入口、一起听单人模式和 QQ 括号伴唱。

- 新增实验性动态插件音源：有界包读取与签名校验、显式权限确认、独立 QuickJS 运行时与账号隔离存储，贯通搜索、播放、歌词、歌单、专辑、歌手、榜单、首页、资料库、收藏、下载与认证等 12 项能力。当前为 RC 工程预览，API 24、完整 UI/OAuth/后台流程与实体真机验收仍未完成，详见 [插件文档](docs/plugin-system/README.md)。
- 后台页面恢复覆盖首页/发现的歌单与榜单、搜索结果与详情、音乐库子页、播客、下载、云盘、私信和全屏播放器；Activity 被系统销毁重建后仍保留当前页面和已加载内容，每日推荐与热榜第二次打开不再空白。
- 首页快捷入口改为每页两张半宽卡片，左侧正常留白、右侧保留 44dp 露出并横向磁吸翻页；移除卡片矩形按压高光与矩形阴影。存储设置新增播放缓存上限（256MB～2GB，LRU 清理）。
- 一起听新增单人模式：房间保持在线但不向对方发送本机指令；QQ 音乐括号歌词可识别为上一句的伴唱（默认关闭）。修复 YouTube Music 登录白屏、汽水音乐扫码登录，并补齐简中/繁中/日文翻译。
- 「设置 → 测试功能尝鲜」新增可选的实验性 MeloX Rear 副屏伴侣入口；该独立应用来自上游 YTRear，不随本版本分发，上游许可证仍待确认。

- 下载与完整更新日志：[GitHub Releases](https://github.com/lladlam/MeloX-Android/releases/tag/0.7.2)
- 详细版本记录：[CHANGELOG.md](CHANGELOG.md)
- 本次版本说明：[docs/releases/0.7.2.md](docs/releases/0.7.2.md)

> [!WARNING]
> 第三方音乐源由用户自行导入并依赖外部服务。发布前用于测试的第三方音源均出现接口失效、限流、DNS/证书错误或返回地址不可播放等情况，因此弥乐无法保证任意第三方源可用。若遇到问题，请前往“设置 → 关于”，拉到页面底部导出弥乐全部日志，并发送至 `lladlam114@gmail.com`。日志可能包含账号状态、歌曲信息、请求地址或其他隐私数据，请在发送前自行检查。

## 项目说明

弥乐 Android 版基于 [MeloX](https://github.com/youshen2/MeloX) 的设计、交互与业务逻辑进行原生 Android 迁移。iOS ／ macOS 版 MeloX 由 [youshen2](https://github.com/youshen2) 制作，并非 Android 版维护者开发。

项目目标不是使用 WebView 套壳，而是尽可能使用 Android 原生能力重新实现弥乐：

- 使用 **Kotlin + Jetpack Compose** 重建界面与交互；
- 使用 **AndroidX Media3 / ExoPlayer** 实现播放、后台音频与系统媒体会话；
- 将 iOS 独有能力映射到 Android 平台能力；
- 在小米 HyperOS 上提供可选的焦点通知 / 超级岛适配，同时保持普通 Android 设备可用；
- 尽可能复刻弥乐 / Apple Music 风格的播放器、歌词、音乐库与过渡动画；
- Apple Watch 相关功能不在 Android 版本的兼容范围内。

Root 权限不是应用正常运行的必要条件；平台增强功能应尽量通过标准 Android 或 OEM 能力实现，并在不支持时降级。

## 当前已实现

### 账号与网易云音乐

- 网易云音乐网页登录；
- `MUSIC_U` Cookie 登录态持久化；
- 登录态搜索、歌曲详情、歌词、播放 URL 与音乐库请求；
- 账号权限变化后刷新播放资源缓存。

### 搜索与音乐库

- 歌曲、歌单、专辑、艺人、用户与播客搜索；
- 我喜欢的音乐；
- 用户歌单；
- 创建公开/私密歌单、向歌单添加歌曲及从自己的歌单移除歌曲；
- 最近播放；
- 歌单、专辑、艺人、歌曲百科与评论详情；
- 排行榜、首页推荐、每日推荐、私人 FM 与心动模式；
- 播客首页、分类、订阅与节目详情；
- 网易云音乐云盘读取、搜索、上传、播放与删除；
- 单曲/集合多选下载、逐次音质选择、离线歌词、自动缓存、存储修复与暂停 / 恢复；
- 保留 app-private 离线副本，并可导出到 MediaStore；本地库可按歌曲、艺术家、专辑和文件夹浏览；
- 从音乐库 / 搜索结果直接播放；
- 音乐库全局搜索，覆盖歌曲、歌单、专辑、艺人与最近播放。

### 播放器

- MiniPlayer；
- Apple Music / Classic 两套全屏播放器外壳；
- 播放队列；
- 下一首播放、手动队列与队列排序；
- 播放 / 暂停、上一首 / 下一首、进度控制；
- 后台播放；
- MediaSession 与系统媒体控制；
- 锁屏播放信息；
- 封面与动态取色背景；
- AutoMix 双播放器预载、音频分析、过渡规划、节拍/速度匹配、淡化与 EQ 包络；
- 31 Hz–16 kHz 十段均衡器、完整预设、系统/播放器音量模式与睡眠定时；
- 播放器共享元素、展开 / 收回动画与横屏播放器；
- 点击播放页曲名打开液态玻璃菜单，可前往艺人、专辑或当前播放队列；
- 歌词画中画，支持上一首／播放暂停／下一首遥控按钮。
- 播放器内置「播放设备」选择器，可切换手机扬声器、蓝牙、有线 / USB 音频输出。
- 局域网「附近的弥乐」：控制、投放（弥乐 PCM）、传输、一起放；协议见 `docs/MELOX-LAN.md`。

### 音质

当前按照弥乐的音质模型接入网易云音乐播放接口：

- 标准：`standard`
- 高品质：`exhigh`
- 无损：`lossless`
- Hi-Res：`hires`
- 高清环绕声：`jyeffect`
- 沉浸环绕声：`sky`
- 超清母带：`jymaster`

实际可播放音质由**歌曲资源、账号权益、地区以及网易云音乐服务端返回结果**共同决定。请求的目标音质不可用时会按可用资源进行降级，并尽量显示服务端实际返回的音质等级。

### 歌词

- LRC 逐行歌词；
- YRC 官方逐字歌词；
- 当前行跟随与滚动；
- 逐字进度渲染；
- 当前行焦点、缩放与颜色过渡；
- Ruby 注音布局、翻译、间奏倒计时、点击跳转与长按分享；
- Apple Music 拖尾滚动、回弹、模糊、高光、长音与刷新率参数；
- EVA 动态构图、18 套具有独立构图/背景/排版/运动参数的 TextPV 模板；
- Skyline 横屏歌词与完整环境文字参数；
- 系统媒体歌词、独立歌词通知和可拖动悬浮歌词；
- ColorOS 锁屏歌词（`lyricInfo`）与 SuperLyricApi 发布，与应用内歌词共用同一时间线。

### HyperOS

- 标准 Android 媒体通知与 MediaSession；
- 可选 HyperOS 焦点通知 / 超级岛桥接；
- OEM 特性通过独立适配层实现，不作为普通 Android 设备的硬依赖。

### 社交、识曲与应用设置

- 一起听房间创建、加入、成员、进度和队列同步；
- 联系人、会话、文字私信与站内分享；
- 麦克风录音、网易云音频指纹、分段与持续听歌识曲；
- 首次启动欢迎与账号页、应用内更新日志、应用内日志查看、赞助者致谢、剪贴板链接识别、页面/首页排序与内容功能开关；
- GitHub 版本检查、更新提示、项目许可与设置重置。

### 第三方音乐源

- 可在“音乐服务”中单独开启第三方音乐源设置并阅读专用协议；该功能完全由本地开关控制，不属于云控范围。
- YouTube Music 接入基于 [lladlam/Square](https://github.com/lladlam/Square) 的双后端架构；InnerTube 模块已 vendored 到 `android/innertube/`，其源自 [Metrolist](https://github.com/mostafaalagamy/Metrolist)，并保留 `com.metrolist.innertube` 包名。
- Spotify 接入参考 [lladlam/Square](https://github.com/lladlam/Square) 的 Spotify backend 设计，播放引擎使用 `librespot-java`，目录和 OAuth 使用 Spotify Web API；Client ID 由用户在应用内自行填写。
- Spotify、YouTube Music 与网易云等源共用同一套 provider-neutral 下载与离线播放链路（`MeloXProviderDownloadStore`）。
- vivo 设备自动尝试接入 OriginOS 原子岛；不提供单独开关，不支持该能力的设备自动保留标准 Android 媒体通知。该适配使用阿里云文档所述的 `notification.superx.*` 本地通知扩展，不依赖 EMAS 远程推送。
- 支持导入 LX Music 兼容的 JavaScript 音乐源，并在受限 QuickJS 运行时中解析播放地址。
- 可配置 CHKSZ 个人 API Key，在当前音源和内置跨平台回退都失败后解析网易云、QQ音乐和酷狗歌曲；配置前需要前往 `api.chksz.com` 注册，目前仅支持 LinuxDo 用户注册。
- 播放顺序：当前音源 → 其他内置音源的严格完整音频匹配（仅在开启“不可用资源从其他平台获取”时）→ 已配置的第三方解析源（仅在第三方开关和协议确认开启时）。全部失败时按不可播放处理，不会用试听片段冒充完整歌曲；歌词仍由弥乐自己的歌词路线处理。
- Bilibili 音源提供实验性「B站定向流量」开关：仅在移动数据下优先选用 B 站官方 CDN 线路，避开 PCDN 与第三方主机；请求身份（Cookie / Referer / User-Agent / WBI 签名）保持不变。是否真正免流由运营商计费策略决定，应用无法查询或保证，详见 [定向流量说明](docs/BILIBILI-DIRECTED-TRAFFIC.md)。

## 平台范围

iOS Live Activity / Dynamic Island 已映射为 Android 媒体通知、歌词通知和可选 HyperOS 焦点通知；iOS 画中画歌词已映射为 Android 原生画中画窗口，并保留可拖动悬浮歌词作为替代。Apple Watch、watchOS 和 macOS 专属目标不属于 Android APK 的迁移范围。

Liquid Glass 在 Android 上使用 Kyant0 `AndroidLiquidGlass` 的原生 Backdrop 实现；具体效果仍受 Android 版本、GPU 与 OEM 合成器能力影响。

## 技术栈

| 用途 | Android 实现 |
| --- | --- |
| UI | Kotlin + Jetpack Compose |
| 导航 / 生命周期 | AndroidX Navigation / Lifecycle |
| 音频播放 | AndroidX Media3 / ExoPlayer |
| 系统媒体控制 | MediaSession / MediaSessionService |
| 网络请求 | OkHttp |
| 图片 / 封面加载 | Coil 3 |
| 异步任务 | Kotlin Coroutines |
| 玻璃 / Backdrop | Kyant0 `AndroidLiquidGlass` / `backdrop` |
| 小米平台增强 | HyperOS Focus Notification 适配层 |

## iOS → Android 平台映射

| 弥乐 / iOS | 弥乐 Android |
| --- | --- |
| SwiftUI | Jetpack Compose |
| NavigationStack | Navigation Compose |
| AVPlayer / AVFoundation | Media3 ExoPlayer |
| MPNowPlayingInfoCenter / Remote Commands | MediaSession |
| Live Activity / Dynamic Island | 标准媒体通知 + 可选 HyperOS 焦点通知 / 超级岛 |
| SwiftUI Mesh / Flowing Light | Compose Canvas / 动态取色背景 |
| Apple Liquid Glass | AndroidLiquidGlass Backdrop 折射、模糊与降级实现 |

## 运行环境

- Android 8.0（API 26）或更高版本；
- Android SDK 37；
- JDK 17；
- Gradle 9.5.0；
- 推荐使用当前版本 Android Studio 打开 `android/` 目录。

部分 RuntimeShader / Backdrop 视觉能力仅在较新的 Android 版本上可用；旧版本会使用降级样式。

## 本地构建

1. 克隆仓库：

   ```bash
   git clone https://github.com/lladlam/MeloX-Android.git
   cd MeloX-Android
   ```

2. 使用 Android Studio 打开：

   ```text
   MeloX-Android/android
   ```

3. 安装 Android SDK 37，并确保使用 JDK 17。

4. 也可以直接使用 Gradle 构建 Debug APK：

   ```bash
   cd android
   ./gradlew :app:assembleDebug --no-daemon --max-workers=2 --stacktrace
   ```

5. 构建产物位于：

   ```text
   android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
   android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
   ```

GitHub Actions 会在 `main` 分支代码更新时自动构建 Debug APK。滚动开发版发布到 `dev-latest` 预发布，使用 Android 默认 debug 密钥签名，不能覆盖安装正式版；正式版使用下述弥乐发布密钥签名。

## Release 签名验证

GitHub Releases 中的正式 APK 使用弥乐发布证书签名。证书 SHA-256 指纹为：

```text
DF:CC:A9:86:5B:87:A4:02:D3:41:98:5A:48:EB:13:2B:D8:67:9D:FA:6D:9D:50:2F:36:5D:D1:62:10:A5:EB:E9
```

可使用 Android SDK Build Tools 验证：

```bash
apksigner verify --verbose --print-certs MeloX-Android-0.5.3.apk
```

## 项目结构

```text
.
├── android/
│   ├── app/
│   │   └── src/main/
│   │       ├── kotlin/com/lladlam/melox/
│   │       │   ├── core/          # 账号、网络、音质、歌词、音乐库模型
│   │       │   ├── playback/      # Media3 播放服务与播放资源解析
│   │       │   ├── platform/      # HyperOS 等平台适配
│   │       │   └── ui/            # Compose 页面、播放器、音乐库、玻璃效果
│   │       └── res/
│   ├── build.gradle.kts
│   ├── innertube/                  # Square/Metrolist InnerTube vendored module
│   └── settings.gradle.kts
├── .github/workflows/             # Android CI / Release
├── LICENSE
└── README.md
```

## 开源项目与特别鸣谢

弥乐 Android 版的主体代码来自弥乐的 Android 迁移工作，同时直接使用或参考了以下开源项目。**各项目仍分别遵循其自己的许可证；弥乐 Android 版的 GPLv3 不会替代第三方项目原有许可证。**

### 上游项目

- [youshen2/MeloX](https://github.com/youshen2/MeloX) — 由 [youshen2](https://github.com/youshen2) 制作的 iOS ／ macOS 原版弥乐，实现、界面、交互与网易云音乐业务逻辑的主要迁移来源；主体采用 GPLv3。

### Android 直接依赖

- [AndroidX / Jetpack Compose](https://github.com/androidx/androidx) — Compose UI、Activity、Lifecycle、Navigation 等 Android 基础能力；主要使用 Apache License 2.0。
- [AndroidX Media3](https://github.com/androidx/media) — ExoPlayer、MediaSession 与后台播放；Apache License 2.0。
- [Jellyfin androidx-media](https://github.com/jellyfin/jellyfin-androidx-media) — 预编译的 Media3 FFmpeg 音频解码器，用于受影响 Android 8.1 设备的 FLAC 软件解码回退；GPL-3.0。
- [Coil](https://github.com/coil-kt/coil) — Compose 图片与封面加载；Apache License 2.0。
- [OkHttp](https://github.com/square/okhttp) — HTTP 网络客户端；Apache License 2.0。
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — Kotlin 协程与异步任务；Apache License 2.0。
- `librespot-java` — Spotify 账号会话、播放和离线缓存；Apache License 2.0。
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube Music 匿名搜索和流地址解析；GPL-3.0。
- [Metrolist InnerTube](https://github.com/mostafaalagamy/Metrolist) — YouTube Music 登录态目录、账号库和歌单操作；GPL-3.0，模块已随本项目 vendored 于 `android/innertube/`。
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — AutoSync 本地逐字歌词对齐推理；MIT。
- [QuickJS Android](https://github.com/HarlonWang/quickjs-wrapper) — LX Music 兼容音源脚本的隔离运行时；分别遵循 wrapper 与 QuickJS 的许可证。
- [ZXing](https://github.com/zxing/zxing) — 二维码生成；Apache License 2.0。
- [Shizuku](https://github.com/RikkaApps/Shizuku) — 可选的免 Root HyperOS 兼容接入；Apache License 2.0。

### Liquid Glass / Backdrop 实现来源

Android 版直接依赖 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 `backdrop` 组件；`MeloXBackdropComponents.kt` 基于其官方 `LiquidButton` 与 `LiquidBottomTabs` 示例适配，在保留弥乐 iOS 尺寸和布局的前提下提供折射、模糊、按压高光与底栏选中透镜。

SF Pro / SF Symbols 为 Apple Inc. 系统字体，仅用于应用图标字形；正文默认全局使用 Android 系统字体，可选的 MiSans 字体按需从 GitHub Release 下载，不打包进 APK；不属于 GPLv3 范围；授权说明见 [SF-Pro-NOTICE.md](docs/SF-Pro-NOTICE.md)。

当前玻璃效果仍属于实验性实现，视觉与兼容性会继续调整。

### API 与 UI 参考项目

- [megadonkatsu/YTRear](https://github.com/megadonkatsu/YTRear) — 实验性可选背屏应用 [MeloX Rear](https://github.com/lladlam/YTRear) 的源码来源；上游仓库未声明许可证。
- [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) — QQ 音乐匿名搜索与歌词协议、纯音乐占位识别、网易云 YRC、Lyricify Lines / Syllable 格式参考；Apache-2.0，详见 [LYRICS.md](docs/LYRICS.md)。
- [guowenye/qishui-api](https://github.com/guowenye/qishui-api) — 汽水音乐原生接口、扫码登录、目录、歌词与播放字段参考；MIT，接入范围与验证记录见 [QISHUI.md](docs/QISHUI.md)。
- [NEORUAA/MeiloX](https://github.com/NEORUAA/MeiloX) — 一个基于 Mei 的仿 Apple Music 网易云音乐客户端，为弥乐 Android 版提供 UI 参考。
- [thlucas1/SpotifyWebApiPython](https://github.com/thlucas1/SpotifyWebApiPython) — Spotify Web API Python 客户端，为 Spotify API 接入提供参考。
- [bromothymolb/bilibili-api-zoku](https://github.com/bromothymolb/bilibili-api-zoku) — Bilibili API 调用与常用功能整合项目，为 Bilibili API 接入提供参考。
- [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) — 酷狗概念版协议、请求签名、每日 VIP 领取及升级参数参考；MIT，详见 [KUGOU-LITE.md](docs/KUGOU-LITE.md)。
- [MoeKoeMusic/MoeKoeMusic](https://github.com/MoeKoeMusic/MoeKoeMusic) — 酷狗概念版桌面账号与签到领会员流程参考；GPL-2.0。
- [MoeKoeMusic/MoeKoeMusic-mobile](https://github.com/MoeKoeMusic/MoeKoeMusic-mobile) — 移动端签到交互与活动结果处理参考；GPL-2.0。MoeKoe 两个项目仅用于行为核对，未复制其代码或界面资源。
- [lladlam/Square](https://github.com/lladlam/Square) — Spotify / YouTube Music 双后端架构参考。
- [lladlam/MeloX-AutoSync](https://github.com/lladlam/MeloX-AutoSync) — 本地逐字歌词对齐与发音映射参考；各组件许可证见随附声明。
- [neteasecloudmusicapienhanced/api-enhanced](https://github.com/neteasecloudmusicapienhanced/api-enhanced) — 听歌识曲与音频指纹运行时参考。
- [mjhydri/BeatNet](https://github.com/mjhydri/BeatNet) — AutoMix 节拍、重拍与速度分析；CC BY 4.0。

### 上游弥乐的参考来源

上游弥乐还特别鸣谢了以下项目；Android 迁移版在歌词 / 网易云接口等部分通过上游实现间接继承了这些设计与研究成果：

- [jayfunc/BetterLyrics](https://github.com/jayfunc/BetterLyrics) — 逐字歌词渲染、光效与动效参考；
- [qier222/YesPlayMusic](https://github.com/qier222/YesPlayMusic) — 网易云接口与播放器实现参考；
- [DanteAlighieri13210914/pv-tool](https://github.com/DanteAlighieri13210914/pv-tool) — 文字 PV 模板与特效的原始实现，按 PV Tool Non-Commercial License 移植。

Text PV / PV Tool、BeatNet 及其他弥乐功能继续按照上游项目要求保留对应的独立许可证与署名。

## 贡献指南

欢迎您为弥乐做出贡献！无论您是古法手敲开发者，还是 Vibe Code 仙人，我们都热烈欢迎！

### 反馈问题与建议

- 请优先前往 [GitHub Issues](https://github.com/lladlam/MeloX-Android/issues) 提交问题或建议；
- 如因网络问题无法访问，请**填写邮件发送至 `lladlam@outlook.com`**，注明**主题**、**问题详细说明 / 建议说明**，并附上**截图与日志**。

### 贡献代码

1. **Fork 本仓库**（注意目标分支是 **`contributor`**，而不是 `main`）；
2. 在您的仓库中修改并提交，请写明清晰的 commit 信息；
3. 向本仓库的 **`contributor`** 分支提交 Pull Request；
4. **在 PR 评论区附上实机录屏或截图**，证明该功能已实际可用；
5. 我们审核通过后合并至 `contributor`，经**再次核验**后合并至 `main`。

> 直接向 `main` 提交的 PR 会被**自动识别**，并引导您改到 `contributor`。

### 贡献要求

以下情况不予采纳：

1. **无用功能不采纳**（与音乐 / 体验无关）；
2. **违反国家法律法规**的内容（如提供盗版音乐服务）；
3. **破坏性更改**。

## 免责声明

本项目出于学习、研究与开源交流目的开发。

- 弥乐不以绕过付费、版权、地区限制或网易云音乐服务限制为目标；
- 使用者应自行遵守所在地法律法规、网易云音乐服务条款以及音乐内容的版权要求；
- 项目调用的第三方服务接口可能发生变化，开发者不保证持续可用；
- 本项目按许可证所述不提供任何担保，使用本项目产生的风险由使用者自行承担。

## 许可证

弥乐主体代码按照与上游弥乐相同的 **GNU General Public License version 3（GPLv3）** 发布，完整条款见 [LICENSE](LICENSE)。

复制、修改或分发本项目时，请遵守 GPLv3 关于源代码提供、版权声明、修改说明以及同许可证分发等要求。

## 赞助致谢

感谢以下朋友对弥乐的赞助与支持！

| 赞助者 | 赞助金额 |
| --- | --- |
| 爱发电用户\_26d40 | CNY 50 |
| 爱发电用户\_aec4c | CNY 6 |
| Bai | CNY 5 |
| 中末 | CNY 8.88 |
| 爱发电用户\_361ef | CNY 20 |
| 爱发电用户\_f8b67 | CNY 12.24 |

## 友情链接

- [LINUX DO](https://linux.do/) — 新的理想型社区。

第三方代码、库、资源与模型继续适用各自的许可证。各贡献者保留其对应贡献的版权。
