# 插件系统仓库审计

审计基线：本地 `main`，提交 `ff87327`，同时检查工作区。已有 `README.md`、`README.en.md`、`README.zh-Hans.md`、`ui/settings/SettingsScreen.kt` 和 `android/rear-companion/` 未提交改动，以及未跟踪的 `docs/plugin-system/`；本次保留这些工作。审计基于静态源码和仓库文件，未在设备上验证运行时或降级行为，不能作为实现完成或安全认证的证明。模型：OpenAI GPT-6.1 Terra；来源：本地仓库源码、工作区及 Git 状态，未使用外部资料。

以下源码路径除另有说明外均相对于 `android/app/src/main/kotlin/com/lladlam/melox/`。

## 构建和模块

- `android/settings.gradle.kts` 包含 app、hidden-api、baselineprofile 和 innertube；`android/app/build.gradle.kts` 使用 Compose，`minSdk = 24`、`targetSdk = 37`，QuickJS wrapper 为 `wang.harlon.quickjs:wrapper-android:3.2.3`。
- `core/music/model/MusicModels.kt` 包含资源、歌曲、分页、音质及播放结果模型。`core/music/provider/MusicProvider.kt` 定义 Provider、能力接口和 registry。歌词模型位于 `core/lyrics/`。

## Provider 与用户路径

- `MusicModels.kt:4-34`：`MusicSource` 是枚举，`MusicResourceId` 由枚举与非空 ID 组成。`MusicProvider.kt:46-92` 的 Provider 身份同样是枚举，搜索、播放和歌词分别是可选接口；声明的 `capabilities` 与实际接口实现没有由 registry 自动验证。
- `MusicProvider.kt:214-231` 的 registry 使用不可变映射并拒绝重复 source。`MeloXMusicProviders.kt:30-76,97-132` 分别创建缓存的应用 registry 和单独的播放 registry，均以代码固定构造内置 Provider。`ProviderPlaybackRuntime.kt:22-39` 缓存播放 registry；安装或启停不能自动刷新这些快照。
- `ui/search/SearchScreen.kt:290-295,433-463` 使用 `remember` 缓存 registry，普通 Provider 搜索通过 `SearchCapability`，也有专门的网易云搜索路径。`core/music/provider/UnifiedMusicService.kt:40,94,112` 按 `MusicSource.entries` 选择聚合来源。动态音源需要贯通选择、单源搜索及聚合，并处理 Compose 更新。
- `playback/ProviderPlaybackResolver.kt:62-82,89-132,169-218,235-238,308-362` 解析 `melox://track/<source>/<id>`，用 registry 查找 `PlaybackCapability`，维护按 URI、认证、音质及补源配置区分的缓存和 in-flight 请求，到期后重新解析；`melox://song/<Long>` 保留旧网易云路径。自定义 URI 来源解析只接受枚举，重建歌曲时仅保留 URI 所携带的字段及内置类型元数据。播放插件若需要附加上下文，必须明确持久化及大小边界。
- `playback/MeloXPlaybackService.kt:626-640` 初始化播放 runtime；`playback/PlaybackTrackIdentity.kt:34,70` 亦按枚举解析身份。现有媒体队列、恢复及正在播放的 resolver 都需评估更新语义，不能只替换 UI registry。
- `ui/player/MeloXProviderLyricsLoader.kt:141-175,282-329,865` 根据播放身份读取歌词、调用 Provider 并接入现有缓存与 AutoSync；插件应提供标准歌词数据，而不是新增播放器或歌词页面。自动匹配和内置来源特判仍需逐分支验证。
- `core/download/MeloXProviderDownloadStore.kt:87,146` 依赖 `DownloadCapability` 并从存储恢复来源；下载与播放是不同能力。`core/music/provider/MeloXUnifiedFavorites.kt:265-283` 将完整歌曲存为 JSON；账号分别由内置 Provider 的 session store 提供（见 `MeloXMusicProviders.kt`），不应暴露给插件。

## 身份和数据安全性

- `MusicModels.kt:21-24` 的 `fromStorageValue` 对未知值**及 null**回退网易云。具体调用包括来源选择、下载记录和本地推荐（分别见 `MusicProviderSelectionStore.kt:27-38`、`MeloXProviderDownloadStore.kt:146`、`core/recommendation/LocalRecommendationStore.kt:103`）。不能直接将未知插件 ID 交给该函数。
- 另一些路径不回退：`MeloXTrackJson.kt:45-48` 对未知歌曲来源返回 null，`ProviderPlaybackResolver.kt:235-238` 和 `PlaybackTrackIdentity.kt:34,70` 只接受已知枚举。尤其收藏读取会跳过无法解码的记录，而后续写入只保存解码后的列表（`MeloXUnifiedFavorites.kt:265-283`）：旧版打开未来插件数据后进行收藏变更可能丢失这些记录。需要无损保留未知记录并测试降级，而不仅是禁止错误归属。
- `MusicProviderSelectionStore.kt:20-25,85-103` 和 `UnifiedMusicService.kt` 依赖固定枚举；`core/music/experience/MusicExperience.kt:190`、`playback/ProviderPlaybackResolver.kt:240` 等存在来源分支。对枚举直接加一个通用 Plugin 常量不能区分多个插件，也不能保留卸载后的原始 ID。

## 现有 LX 脚本运行时

- `core/provider/lxuser/LxUserSourceStore.kt:10-66` 以私有文件和 SharedPreferences 保存导入脚本（上限 9 MB、列表最多 20 条），删除时移除脚本并驱逐 runtime。`LxUserPlaybackResolver.kt:38-85,108-176` 将内置来源及曲目转换为 LX 格式，调用 `musicUrl`、`lyric`、`pic`；它是针对已有歌曲的补源，不是动态音乐平台 Provider。`ThirdPartyPlaybackResolver.kt:21-50,74-91` 将其作为可配置的最后一段补源链。
- `LxUserRuntime.kt:55-90,96-116,122-179,228-327,680-729,832-891` 使用单线程 QuickJS 上下文，提供 callback 网络、Promise 驱动、定时器、编码及加密桥，维护脚本 session。`LxRuntimeThread.kt:8-47` 在线程上串行执行并通过 `future.get()` 等待；异步结果循环有期限，但同步死循环可能使 `evaluate` 无法返回，不能据此承诺可中断或强制超时。未见此代码设置 QuickJS 内存限制或使用隔离进程；需核查 wrapper 的底层能力并做实际压力验证。
- `LxUserRuntime.kt:463-553` 接受 HTTP(S) 目标、脚本指定请求头并读取完整响应体。`core/network/RestrictedHttpClient.kt:18-26,52-77,93-110` 移除共享 cookie jar、认证器与缓存，跨 origin 重定向去除敏感头；但这些措施不是域名许可或私网地址过滤，也不能据此宣称防 SSRF。需在每次重定向及实际 DNS 连接时防私网、回环、链路本地和 DNS 重绑定，并限制请求数、字节和时间；代理与 IPv6 等也需验证。
- `LxUserRuntime.kt:330-385` 尝试移除部分危险全局并限制动态代码，但不能证明引擎没有逃逸或原生漏洞。脚本运行在宿主进程；无限循环、资源耗尽或原生崩溃可能影响应用。即使账号不主动暴露，插件传入的 URL、日志和 HTTP API 仍可能造成插件自身数据外传；凭据隔离必须通过严格 host API 与独立进程边界共同设计，不能仅依赖 QuickJS。

## 兼容性与测试缺口

- 保持内置来源原有 ID、账户与旧 LX 链；插件失效时历史、收藏、下载及队列身份应可保留并显示不可用，不能映射到其他来源。下文列出已经核验的关键读写路径及不能推断为无损的边界。
- 已有 `android/app/src/test/kotlin/com/lladlam/melox/core/music/MusicProviderArchitectureTest.kt`、`UnifiedMusicServiceTest.kt`、`playback/PlaybackFallbackOrderTest.kt`、`core/provider/lxuser/LxUserScriptContractTest.kt` 等覆盖部分内置架构、补源及 LX 契约；尚未发现动态 ID、卸载/降级保留、插件包安全或隔离进程端到端测试。尚未在本次审计中执行这些测试。

## 搜索、队列与身份链

1. `ui/search/SearchScreen.kt:290-295,433-463,803,1766` 选择当前 registry 的 `SearchCapability`，搜索结果以 `MusicTrack` 传给 `ProviderPlaybackCommands.playQueue`；网易云还有历史 UI 路径。`MusicProvider.kt:19-40,46-100` 的声明枚举没有 Download 项，下载由独立接口判断；不能以声明集合替代实际接口判断。
2. `playback/ProviderPlaybackCommands.kt:29-105,144-203` 将曲目转换为 Media3 `MediaItem` 并经 `MediaController` 入服务队列；网易云正整数仍用数字 mediaId 和旧 URI，本地文件使用 content URI，其余使用 provider URI 与 `melox:<source>:<编码资源ID>`。纯 Apple Music 队列走 MusicKit 而绕开 Media3（同文件 41-58 行），不能假定所有来源都经过同一播放和歌词链。
3. `playback/PlaybackTrackIdentity.kt:23-38,64-80` 优先读取 extras，再解码 mediaId；正整数按网易云解释，复合 ID 中来源必须是枚举。歌曲、专辑、歌手 ID 都是 `MusicResourceId`（`MusicModels.kt:27-74`），其来源与资源 ID 的组合才是身份；不能只扩展歌曲或只靠名称匹配。
4. `playback/MeloXPlaybackQueueStore.kt:24-54,69-120` 持久化原始 mediaId、URI、展示字段及有限 extras，恢复时不解析来源，所以未知来源字符串本身可暂时留存；但 source/resource extras、收藏状态和任意未来 metadata 未恢复，后续完整保存也只写白名单字段。非法或空 ID 被跳过，当前索引先按 mediaId 查找。未知来源的可播放性仍被 resolver 和身份解码拒绝，不能据此声称旧版往返完整无损。恢复和保存调用见 `playback/MeloXPlaybackService.kt:2527,2566,2575`。

## 持久化与降级写回矩阵

| 数据/路径 | 读取与写回实际行为 | 迁移风险 |
| --- | --- | --- |
| 本地收藏 `core/music/provider/MeloXUnifiedFavorites.kt:78-152,265-283`；`core/music/model/MeloXTrackJson.kt:12-95,153-213` | 未知歌曲 source 整条跳过；未知歌手/专辑 source 的关联 ID 变 null；未知 metadata type 变 Empty。增删、排序、合并从解码结果重新编码整个列表。 | 旧版修改后未知歌曲被删除，已知歌曲的未知嵌套 ID/metadata 也会丢失。 |
| Provider 下载 `core/download/MeloXProviderDownloadStore.kt:52-76,84-113,140-199,213` | 未知 source 回退网易云，缺文件的记录跳过；添加或删除从内存 records 全量重写 index。路径按 source:id 索引、文件名为该 key 的 SHA-256。 | 旧版写回可将插件记录重标为网易云、丢弃缺文件记录或发生 key 冲突；文件与索引一致性需验证。网易云独立旧下载在 `core/download/MeloXDownloadStore.kt`。 |
| 来源选择 `core/music/provider/MusicProviderSelectionStore.kt:20-45,85-117` | 未知选中 source 读取回退网易云但该分支不立即写回；聚合白名单读取过滤未知值，开关修改时将过滤结果整集合写回。Kugou 还有显式迁移到 Lite 的写回。 | 修改选中或聚合配置会覆盖未来来源；单纯读取与写回风险须区分。 |
| 歌词绑定 `core/lyrics/LyricBindingStore.kt:24-47,57` | 存储 key 为 source:id，未知绑定 provider 读取回退网易云，write 重写该绑定 JSON。 | 可错误请求网易云歌词，后续写入可永久改写绑定来源。 |
| 推荐候选 `core/recommendation/LocalRecommendationStore.kt:87-114` | 未知 source 读取为网易云；`writeCandidateTracks` 重建候选数组。 | 误归属及后续写回；为空 ID 构造抛错时外层 `runCatching` 会返回空列表。推荐特征 `trackKey` 是字符串（22-56 行），不等于完整可恢复歌曲历史。 |
| 设置/诊断 | `ui/settings/MeloXSettingsDetailActivity.kt:121` 用 enum 名称解析并回退网易云；`playback/MeloXPlaybackService.kt:769` 分析 intent 使用 `fromStorageValue`；`platform/cli/MeloXCliReceiver.kt:59` 未知值拒绝。 | 存在 storageValue、enum 名称、mediaId、URI、source:id 多种表示；边界不能统一假设。 |

`MeloXTrackJson` 为目前已查明的完整收藏歌曲序列化；以上矩阵覆盖搜索到播放主要持久化边界，但不声称整个应用所有偏好、第三方缓存或每个 UI 分支已穷举。未知字段的保留应按旧版读—修改其他项—写—新版恢复场景验证；仅能在新版实现保留并不能改变已经发布的旧版写法。

## 播放缓存、历史和歌词

- `playback/MeloXPlaybackService.kt:626-678` 创建 runtime、网易云及 Provider resolver、补源链和 Media3 `CacheDataSource`；`playback/MeloXMediaCache.kt:15-20` 是 cacheDir 下 LRU 的媒体字节缓存。`ProviderPlaybackResolver.kt:41-58,62-82,89-132,169-219` 另有进程内 URL/headers LRU 和 in-flight 去重：key 包含原 URI、认证、音质、补源配置，只有明确 expiry 才按时间过期，结果 URI 用作解析后 DataSpec key；不要把这两层缓存的 key 或失效策略混为一谈。未发现插件安装/禁用版本或更新代数参与此 key；服务持有的 resolver 与 registry 生命周期也必须纳入刷新设计。
- 历史不是通用 Provider 历史：`playback/MeloXPlaybackService.kt:330-337,2361-2377` 只对实际出声且 mediaId 可转 Long 的曲目调用 `MeloXPlaybackHistoryReporter` 和旧下载自动缓存计数；`playback/MeloXPlaybackHistoryReporter.kt:11-16` 上传网易云 weblog。`core/library/NeteaseLibraryClient.kt:314` 与 `NeteaseLibraryCache.kt:150-151` 是网易云最近播放读取/缓存。复合 provider mediaId 不进入上述数字历史；这不证明不存在其他统计数据。`LocalRecommendationStore.kt:22-56` 另存字符串 trackKey 的个性化特征，不能当作通用历史列表。
- `ui/player/MeloXProviderLyricsLoader.kt:141-175,209-248,282-329,743-800,825-866` 从当前身份、绑定、缓存和 Provider `LyricsCapability` 获取标准 `LyricsDocument`；自动选择还明确针对网易云、QQ、酷狗及 AMLL，本地/Bilibili 存在专门分支。插件自己的歌词接入与被自动搜索候选是不同路径，不能仅接通前者就断言后者生效。
- `core/autosync/AutoSyncCoordinator.kt:44-65,75-95,119-173` 在设置启用、无逐字时间且可用模型和本地音频时生成；校验歌词 hash、模型 key，提供 audioKey 时还校验音频身份。`core/autosync/AutoSyncTimelineStore.kt:20-38,47-58` 用 trackKey 的 32 位 hashCode 文件名保存时间轴，内容使用歌词 SHA-256；trackKey 哈希碰撞及跨来源隔离应测试。`MeloXPlaybackService.kt:686-695,2487-2488` 桥接当前曲目与音频获取并在服务关闭时解除。未实际运行模型、远程音频或切曲并发测试。

## 实施前验证清单

1. 为内置原始 ID、特殊字符与同资源 ID 的不同来源建立序列化、URI、MediaItem、队列重启测试；覆盖来源缺失、禁用、重新安装及旧版读取后修改其他记录再升级，逐字节检查未知记录和扩展字段。注意旧版不能通过新版补丁追溯修复。
2. 覆盖选源、聚合白名单、搜索分页、能力缺失和 UI 更新；安装、升级、禁用后 UI registry、播放服务 resolver、进行中请求和 URL/字节缓存的失效与竞争，兼测内置及 LX 回归。
3. 覆盖收藏嵌套身份、下载文件/索引及离线播放、网易云历史和非网易云历史预期；歌词直接来源、绑定、自动匹配、AutoSync 缓存、音频和模型更换、切曲取消及键冲突。
4. 对 QuickJS 同步死循环、中断、堆限制、Promise/定时器、进程死亡、API 24 兼容性做设备实测；对网络请求、重定向、DNS 重绑定、IPv4/IPv6、代理、响应大小及媒体 URL/headers 的宿主消费链做安全验证。当前静态阅读既未证明隔离，也未证明 wrapper 不支持某项底层能力。
5. 本次为文档审计，未执行 Gradle 测试、Android 构建、设备测试或外部网络验证；上述缺口是后续验收条件而非已通过的测试。
