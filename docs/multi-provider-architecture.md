# MeloX Android 多音乐服务架构

[English](../README.en.md)

本文档约束 MeloX Android 在网易云音乐、QQ 音乐、酷狗音乐等服务之间的边界，并确保上游 MeloX iOS（目前只实现网易云音乐）未来继续可以低成本迁移到 Android。

## 核心原则

1. **统一数据，不强行统一产品能力。**
   - `MusicTrack`、`MusicResourceId`、`PlaybackResolution`、`LyricsDocument` 等是跨平台语义。
   - 云盘、私人 FM、心动模式、一起听、网易云播客、私信、识曲等能力不要求 QQ/酷狗提供伪实现。
2. **统一 MeloX 页面骨架，Provider 只决定数据、模块可见性与额外能力。**
   - 首页、发现、搜索、音乐库、歌单/专辑/歌手详情、MiniPlayer、Now Playing、歌词、Liquid Glass、转场等都优先复用从 MeloX iOS 迁移到 Android 的现有 UI。
   - 不为网易云、QQ、酷狗长期维护三套同用途页面；同一音乐概念必须优先使用同一 Renderer / Layout。
   - Provider 缺少某项能力时隐藏对应 section/action，不显示假数据或“占位功能”。
   - Provider 比网易云多出的真实能力，以附加 section/action 插入共享 MeloX 页面，不为此复制整页 UI。
   - Provider 名称/来源是内容元数据，不应让整个 App 变成不同品牌的视觉界面。
3. **网易云模式继续以 MeloX iOS 为迁移基准。**
   - Android 的多 Provider 架构不得反向要求 iOS 支持 QQ/酷狗。
   - iOS 新增 UI 功能首先进入共享 MeloX UI；若它依赖网易云专属能力，则该 section 只对 NetEase 可见。
   - iOS 新增的网易云专属业务应优先进入 Android 的 NetEase capability，而不是扩大所有 Provider 的强制接口。
4. **Provider 只负责“怎么从平台得到数据/执行平台操作”，Core 负责“播放器和 UI 需要什么”。**
5. **用户登录态只保存在本机。**
   - MeloX 不要求中转服务器保存用户的音乐平台 Cookie / Token。
6. **跨平台聚合显式 opt-in。**
   - `unifiedEnabled` 默认 `false`。
   - `automaticSourceFallback` 默认 `false`。
   - 默认一次只使用用户选择的一个音乐服务。

## 四层结构

```text
Shared MeloX UI (iOS migration target)
  Home / Explore / Search / Library / Detail / Player / Lyrics / Glass
        |
UI Capability Adapter
  show/hide shared sections + inject provider-only sections
        |
Capability + Domain
  Search / Playback / Lyrics / Album / Artist / Playlist / ...
        |
Provider Protocol
  NetEase EAPI/WEAPI / QQ Music requests / Kugou requests
```

`MusicExperience` 只描述功能布局/可见能力，不能演变成三套互不兼容的视觉页面。

## Domain ID

禁止继续假设歌曲 ID 是 `Long`。

```text
MusicResourceId(
    source = QQMusic,
    value = "0039..."
)
```

资源身份必须由 `(source, value)` 共同确定。

- 网易云：数字 ID，保留为字符串进入公共层；旧播放队列继续兼容数字 `mediaId`。
- QQ 音乐：以 `songmid` 为公共 ID，`media_mid` 等放 `ProviderTrackMetadata.QQMusic`。
- 酷狗：以歌曲 hash 为公共 ID，`album_audio_id` / `album_id` 放 `ProviderTrackMetadata.Kugou`。

## Capability 规则

`MusicProvider` 本身只声明身份与能力；功能通过可选 Capability 提供。

公共第一层能力：

- Search
- Playback
- Lyrics
- Playlist
- Album
- Artist
- Library

可选第二层能力：

- Comments
- Rankings
- HomeRecommendations
- DailyRecommendations
- Favorite / PlaylistWrite

Provider-native 第三层能力示例（网易云）：

- CloudMusic
- PrivateFm
- HeartMode
- ListenTogether
- Messages
- Recognition
- Podcasts

QQ/酷狗没有对应能力时不要显示入口，不要返回“假数据”占位。

## iOS -> Android 新功能迁移决策

每次 MeloX iOS 更新按以下顺序分类：

1. **纯 UI / 播放器 / 歌词表现**
   - 直接进入共享 MeloX UI / common core。
   - 所有 Provider 自动受益，不在 QQ/Kugou 下复制一份页面。
2. **通用音乐概念，且 Provider 有等价能力**
   - 新增或扩展 Capability / adapter。
   - 仍复用同一 MeloX UI，只替换数据和操作实现。
3. **网易云业务功能**
   - 保持 `Netease*` 实现或新增 NetEase-specific capability。
   - 共享页面对应 section 在 NetEase 模式显示，QQ/Kugou 隐藏。
4. **其他 Provider 独有功能**
   - 新增 provider-specific capability。
   - 作为共享页面的附加 section/action 出现，除非交互模型完全无法复用才允许新增专用页面。

因此，Android 多平台扩展不能破坏“iOS 新功能 -> Android 共享 UI -> Provider 能力适配”的迁移通道。

## 播放兼容策略

现有播放器大量使用纯数字 `mediaId`，这些继续视为网易云歌曲。

新增 Provider 使用带命名空间的 Media ID：

```text
melox:qq_music:<songmid>
melox:kugou:<hash>
```

Media URI：

```text
legacy: melox://song/<neteaseLongId>?quality=...
new:    melox://track/<source>/<providerId>?qualityTier=...
```

旧 URI 继续由 `NeteasePlaybackResolver` 原路径解析；新 URI 由 `ProviderPlaybackResolver` 解析。

NetEase 的播放历史、相似歌曲自动推荐、云盘、下载、智能 AutoMix 分析等原有业务仍然以数字 ID 分支工作。非网易云歌曲遇到这些 NetEase-only 分支时应安全跳过，而不是伪造数字 ID。

## UI 规则

**默认统一：**

- 根导航：`首页 / 发现 / 音乐库 / 设置 / 搜索`
- 搜索框、分类选择、歌曲/歌单/专辑/歌手结果布局
- 首页 section 的视觉组件和排列体系
- 音乐库列表/卡片/详情骨架
- 歌单、专辑、歌手、排行榜详情 Renderer
- MiniPlayer / Now Playing / 队列 / 更多操作 Sheet
- 歌词四种样式及其动画
- Liquid Glass、转场、手势、字体与间距体系

**Provider 只允许改变：**

- 某个 section/action 是否可见
- section 的实际数据
- 平台来源标记、账号信息和版权/音质状态
- 平台独有的真实额外 section/action
- 登录流程和 Provider-specific 设置

例如：QQ 没有网易云云盘就隐藏“云盘”；网易云有心动模式就仅网易云显示；酷狗有平台特有能力则作为额外卡片插入，而不是把整个“发现”页改成另一套 UI。

## 聚合模式

聚合属于上层组合器，不属于单个 Provider：

```text
UnifiedMusicService
  |- NeteaseProvider
  |- QQMusicProvider
  `- KugouProvider
```

默认关闭。

聚合开启后仍应由用户明确选择参与的平台；“某平台不可播 -> 自动偷换另一个来源”不得作为默认行为。

聚合搜索返回的每一项必须保留来源和完整展示元数据（包括封面）；聚合器不得为了统一结果而丢掉 Provider 已提供的 artwork / album / artist 信息。

## 歌词性能规则

- 网络请求、QRC/KRC 解密、LRC 解析必须在渲染热路径之外完成。
- 同一歌曲的 `LyricsDocument` 应在不同歌词样式间复用，不因切换 Apple Music / EVA / TextPV / Skyline 重复请求与解析。
- 只有平台真实返回的逐字时间轴才默认进入高频逐字绘制。
- 普通 Provider LRC fallback 不应自动膨胀为 synthetic per-grapheme timing；否则会在没有真实逐字数据的情况下额外增加 60Hz glyph 绘制开销。
- 网易云已有的用户可选“伪逐字”行为保持兼容，不因多 Provider 改造被全局关闭。

## 当前实现阶段

当前分支已建立：

- Provider-neutral domain models
- Capability contract
- 本地 Provider selection store 与显式聚合白名单
- `NeteaseProvider` 兼容适配
- `QQMusicProvider` 登录 / Catalog / LRC / vkey / 写操作
- `KugouProvider` Android 签名 / Catalog / KRC / HTTPS 播放 / 歌单写操作
- Provider-aware Media3 URI / queue bridge
- 统一 Search launch event；Provider 专辑/歌手操作会进入指定搜索类别并自动搜索
- Provider 歌词 LRU cache；普通 Provider LRC 禁止 synthetic word timing

后续 UI 工作必须持续把当前临时 `Provider*Screen` 的功能收敛进 MeloX iOS 迁移页面/共享 Renderer。`Provider*Screen` 只作为过渡适配层，不得继续发展为另一套独立设计系统。
