# 插件开发预览交付清单

## 结论

当前代码已贯通 12 项能力及主要原生入口，Android Debug/AndroidTest 构建通过；510 项 Kotlin 测试中
507 项通过、3 项跳过，SDK 10 项通过。API 35 转译模拟器已有真实设备子集依据，
**不是正式稳定发布验收通过**；最新 [RC 安全与设备报告](rc-acceptance.md)。
完整证据见 [verification.md](verification.md)，缺口见 [remaining-work.md](remaining-work.md)。

## 核心链路

SAF 有界读取 → 归档与 Manifest 校验 → 权限预览/确认 → 默认禁用安装 → 启用时隔离运行时
方法预检 → 动态 Registry 发布 → 原生页面与 Provider → Media3 / 歌词 / 收藏 / 下载。
升级先检查权限批准，再准备候选；失败保留旧包与 Provider。禁用、卸载及账号变更失效共享
generation；持久化资源使用 SourceId，未知来源不回退内置源。卸载保留用户历史及账号数据。

## 能力与证据

下表“接线”指源码实现；Playback 的 QuickJS/Media3 与底层运行时、Keystore 等已有 API 35
子集实测，其余各能力的完整原生 UI 仍未完成验收。Node 示例在 vm 中运行，
不提供 Android 沙箱证明。标准模型由 Kotlin 显式校验，尚未全量代码生成或完成全部恶意字段的双端一致性测试。

| Capability | 原生接线 | 自动化依据 |
| --- | --- | --- |
| Search | 主搜索、来源筛选、跨源显式选入、分页 | CatalogProvider、ResultValidator、UnifiedMusicService；catalog/test-tone 示例 |
| Playback | 队列、Media3 专用出口、实际音质、失效 URL 一次重解析 | AudioContract、身份/播放回归；示例 WAV；broker 实际 206/416 |
| Lyrics | 标准同步/非同步歌词映射、长度/时序校验 | AudioContract、示例歌词 Schema |
| Playlists | 列表、搜索、详情、歌曲与分页 | CatalogProvider；catalog Node 示例 |
| Albums | 搜索、详情、分页 | CatalogProvider；catalog Node 示例 |
| Artists | 搜索、详情、分页 | CatalogProvider；catalog Node 示例 |
| Rankings | 榜单列表与曲目 | CatalogProvider；catalog Node 示例 |
| HomeFeed | 主来源首页、显式推荐候选 | CatalogProvider；catalog Node 示例 |
| UserLibrary | 主来源资料库、账号摘要与收藏目录 | CatalogProvider；catalog Node 示例 |
| Favorites | 歌曲菜单状态查询/写入、账号 storage | CatalogProvider 严格 boolean/void；catalog 持久化测试 |
| Download | 能力门控、专用出口、配额/取消、并发 2、菜单/列表错误与会话内重试 | AudioContract、Provider 能力与身份回归；catalog resolveDownload 示例；列表交互待设备验证 |
| Authentication | API Token、公开客户端 OAuth2/PKCE、多账号、刷新 | CredentialPolicy、OAuthContract、HardeningPolicy；api-token 示例与 Manifest fixture |

方法声明与宿主/SDK 一致性另由 `PluginProtocolContractTest` 和 Node Schema 测试覆盖。
Keystore、实际 OAuth 服务和完整下载到磁盘的正确性仍需设备测试。

## 修改文件及用途索引

下述 Android 路径均相对 `android/app/src/main/kotlin/com/lladlam/melox/`。
这是本次插件系列工作区的功能索引，包含前阶段累积内容；开发预览检查点 `4af356f` 已推送，
后续 RC 修改保留本地，尚未形成正式发布。

### 协议、生命周期与安全

| 路径 | 用途 |
| --- | --- |
| `core/plugin/PluginManifest.kt`, `PluginVersion.kt`, `PluginPermissions.kt` | API/能力/版本/作者元数据解析及升级权限比较 |
| `core/plugin/PluginArchiveReader.kt`, `PluginPackageStore.kt` | 有界 ZIP、路径验证与原子包存储 |
| `core/plugin/MeloXPlugins.kt`, `PluginManager.kt` | 全局生命周期、恢复、回滚、权限批准、诊断 |
| `core/plugin/PluginRuntime.kt`, `PluginIsolatedService.kt`, `PluginIpcPolicy.kt` | 隔离 QuickJS、Promise、Messenger 身份/token/配额、错误脱敏与资源回收 |
| `core/plugin/PluginHttpBroker.kt`, `PluginStorage.kt` | 精确域名、公共 DNS、重定向与体积限制、账号级普通存储 |
| `core/plugin/PluginAccountVault.kt` | Keystore 密文、账号/策略隔离、会话戳与刷新原子替换 |
| `core/plugin/PluginOAuth.kt`, `PluginOAuthCallback.kt`, `PluginOAuthCallbackActivity.kt` | PKCE、外部授权、严格回调与 Token 兑换 |
| `core/plugin/PluginMusicProvider.kt`, `PluginCatalogCodec.kt`, `PluginResultValidator.kt` | 12 能力调用及标准模型验证/映射 |
| `core/plugin/PluginArtwork.kt`, `PluginArtworkFetcher.kt` | 带来源封面 URI、Coil 专用出口、像素与字节限制 |
| `core/plugin/PluginTrackLink.kt` | 保留动态身份的分享链接解析 |
| `MeloXApplication.kt`；`android/app/src/main/AndroidManifest.xml`（仓库根目录相对路径） | 启动恢复、图片组件及隔离服务/回调/链接 Activity 注册 |

### SourceId、播放与原生入口

- `core/music/model/SourceId.kt`、`MusicModels.kt`、`MeloXTrackJson.kt`：身份、标准模型、序列化。
- `core/music/provider/MusicProvider.kt`、`MeloXMusicProviders.kt`、`MusicProviderSelectionStore.kt`：动态目录、能力接口、主来源选择与变更流。
- 同目录 `MeloXLegacyUiBridge.kt`、`MeloXUnifiedFavorites.kt`、`ProviderCollectionPaging.kt`、
  `TrackAggregation.kt`、`UnifiedMusicService.kt`：旧 UI 映射、收藏保留与分页聚合。
- `core/provider/local/LocalProvider.kt`、`core/provider/spotify/SpotifyTrackMatcher.kt`、
  `core/recommendation/LocalRecommendationEngine.kt`、`LocalRecommendationStore.kt`：内置边界及推荐身份。
- `core/download/MeloXProviderDownloadStore.kt`：动态下载、索引、取消、配额和会话内错误任务/重试状态。
- `lan/MeloXLanModels.kt`、`platform/cli/MeloXCliReceiver.kt`：LAN/CLI 动态身份。
- `playback/PlaybackTrackIdentity.kt`、`ProviderPlaybackCommands.kt`、`ProviderPlaybackResolver.kt`、
  `PluginMediaDataSource.kt`、`MeloXPlaybackService.kt`：队列、媒体解析、受控出口与缓存分流。
- `playback/LxUserPlaybackResolver.kt`、`PlaybackFallbackChain.kt`、`ThirdPartyPlaybackResolver.kt`：动态来源与内置/LX 补源边界。
- `ui/MeloXApp.kt`：根页面动态主来源路由。
- `ui/provider/PluginSourcePage.kt`、`PluginServiceSection.kt`、`ProviderServicesScreen.kt`、
  `ProviderSettingsHub.kt`：原生标签、服务中心与账号区域。
- `ui/search/SearchScreen.kt`、`PluginCatalogBrowser.kt`、`PluginFavoriteDialog.kt`：搜索、目录/详情、分页与原生收藏交互。
- `ui/settings/PluginSettings.kt`、`PluginAccounts.kt`、`SettingsScreen.kt`：权限预览、安装/更新/卸载、多账号和诊断。
- `ui/sharing/PluginTrackLinkActivity.kt`：外部链接原生确认播放，不自动安装或启用。
- `ui/library/LibraryScreen.kt`、`MeloXBatchDownloadSheet.kt`、`ui/discovery/MeloXDiscoveryScreens.kt`：目录、下载及发现身份适配。
- `ui/player/MeloXProviderSongActionsOverlay.kt`、`MeloXProviderLyricsLoader.kt`、`MeloXIOSNowPlayingScene.kt`、
  `MeloXPlayerUi.kt`、`ui/song/MeloXSongWikiActivity.kt`：歌曲操作、封面、歌词及旧菜单边界。

### 测试

相对 `android/app/src/test/kotlin/com/lladlam/melox/`：

- `core/plugin/PluginArchiveReaderTest.kt`、`PluginManagerTest.kt`：包格式与生命周期/升级批准/失败回滚。
- `core/plugin/PluginProtocolContractTest.kt`、`PluginResultValidatorTest.kt`、`PluginCatalogProviderTest.kt`、
  `PluginAudioContractTest.kt`：能力方法、目录、音频与歌词协议。
- `core/plugin/PluginPolicyTest.kt`、`PluginCredentialPolicyTest.kt`、`PluginOAuthContractTest.kt`、
  `PluginHardeningPolicyTest.kt`、`PluginTrackLinkTest.kt`：地址/凭据/PKCE/IPC/权限/图片/链接边界。
- `core/music/model/SourceIdTest.kt`、`DynamicTrackPersistenceTest.kt`、`PluginLegacyCompatibilityTest.kt`、
  `core/music/provider/FavoriteUnknownSourceTest.kt`、`playback/PlaybackTrackIdentitySourceTest.kt`：身份与未知来源保留。
- `core/music/MusicProviderArchitectureTest.kt`、`UnifiedMusicServiceTest.kt`：注册目录与跨源回归。

### SDK、示例与文档

- `plugin-sdk/src/{index,manifest,types}.ts`：宿主无关 TypeScript 协议。
- `plugin-sdk/schema/{manifest,protocol-v1}.schema.json`：Manifest 与标准返回 Schema。
- `plugin-sdk/tools/{cli,manifest,zip}.mjs`：校验、构建/打包及 ZIP。
- `plugin-sdk/test/{sdk,catalog}.test.mjs`、`test/fixtures/manifest-cases.json`：Node 与共享契约测试。
- `plugin-sdk/examples/{test-tone,catalog,api-token}/`：各含 `manifest.json`、`index.ts`、`README.md`。
- `plugin-sdk/{package.json,package-lock.json,tsconfig.json,tsconfig.check.json,.gitignore,README.md}`：可复现构建及使用说明。
- `docs/plugin-system/`：协议、运行时、认证、进度、剩余工作、验证、设备验收及本清单。

工作区前序 README 与 `android/rear-companion/` 内容含其他任务的修改，不将其视为本轮插件新增验证。

## 产物与发布前缺口

### RC 新增文件与用途

- `core/plugin/PluginPackageTrust.kt`：完整性、RSA 公钥/签名、精确包哈希批准。
- `core/plugin/PluginJson.kt`：递归 JSON 解析前的字节/深度边界。
- `core/plugin/PluginMediaFormats.kt`：不支持的自适应媒体/DRM 显式拒绝。
- `android/app/src/androidTest/kotlin/com/lladlam/melox/core/plugin/PluginRcDeviceRunner.kt`：
  真实 UID、Keystore、取消/watchdog、文件生命周期与队列验收。
- 同目录 `PluginRcOnlineChecks.kt`：公开合成 PNG/WAV 的真实 Coil/Media3 验收。
- 同目录 `PluginRcCatalogChecks.kt`：SDK 签名包在实际隔离 QuickJS 中执行全部 12 能力。
- `android/app/src/test/kotlin/com/lladlam/melox/core/plugin/PluginRcSecurityTest.kt`：签名/篡改/资源/HTTP/媒体回归。
- `android/app/src/test/kotlin/com/lladlam/melox/core/music/model/PluginDataUpgradeTest.kt`：嵌套未知字段与旧数据。
- `android/app/src/test/kotlin/com/lladlam/melox/core/recommendation/RecommendationSourceIdTest.kt`：推荐身份回归。
- `playback/MeloXPlaybackQueueStore.kt`：新增到改动清单，保存完整动态 Track JSON 和身份 extras。
- `scripts/plugin-rc-device-check.sh`：安装、运行 instrumentation、校验 PASS 与外部非导出拒绝。
- `plugin-sdk/tools/package-trust.mjs`、`test/package-trust.test.mjs`：SDK 签名包与结构回归。
- `docs/plugin-system/rc-acceptance.md`、`sdk-consistency.md`：审查、设备复现和能力核对。

其余 RC 修改集中于上述已列出的包存储/Manager/设置确认、账号清理、HTTP/JSON/返回校验、
Track codec、下载/推荐索引及队列/播放路由，详见最新 verification.md 与 git 工作区差异。

Debug APK 位于 `android/app/build/outputs/apk/debug/`（arm64-v8a、armeabi-v7a）。三个示例包路径
及结果见 verification.md。开发预览检查点 `4af356f` 已推送，RC 后续修改保留本地；外部文档站 PR/合并/部署尚未完成。

正式发布前至少需要 API 24 与较新设备完整执行验收，完成安全故障注入、全仓通用调用点与
数据无损确认，并处理发布者信任、损坏包管理、数据清理和剩余交互；不能用单测通过替代。
