# SDK / 宿主协议一致性核对（RC）

API 版本保持 v1。TypeScript：`plugin-sdk/src/types.ts`；返回 Schema：
`plugin-sdk/schema/protocol-v1.schema.json`；Manifest 方法契约：
`plugin-sdk/schema/manifest.schema.json` 的 `x-capability-methods`。
Android 的 `PluginCapability.methods` 由共享方法表测试逐项比较，启用时执行 `__validate`
检查全部声明方法。当前未发现 SDK 声明了方法但 Android 没有调用路径的 Capability。
最终签名 catalog 包还在 API 35 的真实隔离 QuickJS 中完成全部 12 项 Provider 调用、详情/
分页与跨 Context 收藏写入；下表的目录/库/认证状态均不再只是 Node 或 stub invoker 测试。
这不代表所有能力 UI、OAuth 平台或完整下载文件验收通过。

## 最新代码一致性补齐（尚未验证）

新增 `PluginWireContract`，所有标准 Provider 返回先检查结构和显式可选字段类型，再映射模型。
`plugin-sdk/test/fixtures/wire-cases.json` 为 Kotlin/Node 共享的完整字段及方法清单；新增类型变异
测试，覆盖数组、嵌套对象和可选字段。Schema 新增可空 `accountSummary`；TS 专辑 artists
改为可选，与现有 Schema/宿主一致。文本字段长度使用 Unicode code point，ID 另限 UTF-8 字节。
网络授权、URL/DNS、集合请求大小、媒体格式和歌词排序仍由宿主语义/策略层额外检查，
Schema 单独通过不代表允许网络或媒体访问。本批次 Kotlin、TS、Schema 和运行时结果均 **NOT_RUN**。
`MELOX_RC_WIRE_FIELDS=1` 将同一 fixture 送入真实隔离 QuickJS，逐方法比较全部原始字段，
再执行全部 Capability Provider 映射；此 runner 也尚未运行，不代表完整 UI 或 HTTP 出口通过。

| Capability | TS 方法 | 返回 Schema | Kotlin / UI 路径 | 主要验证 |
| --- | --- | --- | --- | --- |
| search | search | searchPage | searchSongs → SearchScreen | ResultValidator、CatalogProvider、catalog/test-tone |
| playback | resolvePlayback | playbackResolution | resolveAudio → ProviderPlaybackResolver → Media3 | AudioContract、RcSecurity、API35真实解析/播放/seek/切歌 |
| lyrics | lyrics | lyrics | 标准歌词 → MeloXProviderLyricsLoader | AudioContract、示例歌词；歌词 UI 未验收 |
| playlists | playlists/searchPlaylists/playlistDetail | playlistPage/playlistDetail | CatalogCodec → PluginCatalogBrowser | CatalogProvider、catalog Node |
| albums | searchAlbums/albumDetail | albumPage/albumDetail | CatalogCodec → PluginCatalogBrowser | CatalogProvider、catalog Node |
| artists | searchArtists/artistDetail | artistPage/artistDetail | CatalogCodec → PluginCatalogBrowser | CatalogProvider、catalog Node |
| rankings | rankings/rankingTracks | rankingPage/searchPage | CatalogCodec → 主来源发现/目录 | CatalogProvider、catalog Node |
| homeFeed | homeFeed | homeFeed | 主来源首页/显式本地推荐候选 | CatalogProvider、catalog Node |
| userLibrary | accountSummary/userPlaylists/savedAlbums/followedArtists | account或null/playlistPage/albumPage/artistPage | 主来源资料库/目录 | CatalogProvider、catalog Node |
| favorites | isFavorite/setFavorite | favoriteState/writeAcknowledgement | 原生歌曲菜单/收藏对话框 | 严格boolean/null、catalog普通存储测试 |
| download | resolveDownload | playbackResolution | 下载菜单/Store/列表 | AudioContract、catalog；完整下载UI未验收 |
| authentication | authenticationStatus | authenticationStatus | 状态Provider/PluginAccounts/宿主OAuth及vault | CredentialPolicy、OAuthContract、api-token、API35合成Keystore |

## 本轮一致性修正

- 目录封面在 Schema 中明确为字符串，并在 Kotlin 映射到带来源的受控 URI。
- SDK 已有的 account avatarUrl 接入 Kotlin 受控图片映射，Schema 补相同字段。
- HLS/DASH/DRM 不受支持：Kotlin 拒绝已知格式/URL/DRM字段，媒体出口拒绝自适应 MIME；
  Schema 拒绝已知 format 标签和 DRM/license 字段，SDK README 明确仅渐进式音频。
- 所有返回值在宿主递归解析前检查最大 128 KiB 与深度32；脚本/Manifest 在安装时独立限额。
- HTTP 参数拒绝 Android JSONObject 的隐式字符串转换，与 TS 的字符串模型一致。
- 新 SDK 包提供完整性清单与可选签名；签名 envelope 不更改 12 能力的标准请求/返回模型。

## 错误与不支持能力

插件只声明已实现的能力；未声明能力隐藏或返回 UNSUPPORTED_OPERATION，不提供空实现冒充。
第三方错误由作者转换为 `PluginErrorCode` 或标准版权/登录/不可用状态；脚本错误的任意
message 不透传 UI，只保留白名单错误码。宿主策略拒绝（域名、字节/深度、媒体格式等）不能
靠返回一个 Schema 合法对象绕过。没有自动重放失败写请求。

## 不能据此宣称的完整一致性

TypeScript 无法静态证明整数范围、字节长度、权限或生命周期；JSON Schema 是结构约束，
不是 Android 网络/授权策略实现。URI/DNS/实际 MIME、UTF-8 ID 字节限额、账号会话、总结果
字节/深度及分页请求一致性在宿主继续检查。部分 Unicode 长度、可选字段和全部恶意输入的
双端等价接受性仍缺少完整 fixture，模型也尚未从同一 Schema 生成。

现有 510 项 Kotlin、10 项 Node 和设备子集不能证明“所有字段在所有输入上等价”。正式发布
前仍需补这一矩阵及旧 APK 回归。Windows/Web 只是宿主无关协议的平台标识，不是本仓库已
交付的运行时；插件作者不直接调用 Android/Media3/Compose。
